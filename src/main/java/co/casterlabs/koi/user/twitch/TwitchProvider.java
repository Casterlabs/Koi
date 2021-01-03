package co.casterlabs.koi.user.twitch;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.koi.Koi;
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.events.ViewerJoinEvent;
import co.casterlabs.koi.events.ViewerListEvent;
import co.casterlabs.koi.user.Client;
import co.casterlabs.koi.user.ConnectionHolder;
import co.casterlabs.koi.user.IdentifierException;
import co.casterlabs.koi.user.KoiAuthProvider;
import co.casterlabs.koi.user.User;
import co.casterlabs.koi.user.UserPlatform;
import co.casterlabs.koi.user.UserProvider;
import co.casterlabs.twitchapi.helix.HelixGetUsersRequest;
import co.casterlabs.twitchapi.helix.HelixGetUsersRequest.HelixUser;
import co.casterlabs.twitchapi.helix.TwitchHelixAuth;
import lombok.Getter;
import lombok.NonNull;
import xyz.e3ndr.watercache.WaterCache;

public class TwitchProvider implements UserProvider {
    private static @Getter Map<String, ConnectionHolder> connectionCache = new ConcurrentHashMap<>();
    private static WaterCache cache = new WaterCache();

    static {
        cache.start(TimeUnit.MINUTES, 1);
    }

    @Override
    public synchronized void hookWithAuth(@NonNull Client user, @NonNull KoiAuthProvider auth) throws IdentifierException {
        try {
            TwitchTokenAuth twitchAuth = (TwitchTokenAuth) auth;

            HelixGetUsersRequest request = new HelixGetUsersRequest(twitchAuth);

            HelixUser profile = request.send().get(0);

            user.getConnections().add(getMessages(user, profile));
            user.getConnections().add(getFollowers(user, profile));
            user.getConnections().add(getStream(user, profile));
            user.getConnections().add(getProfile(user, profile));

            User asUser = TwitchUserConverter.transform(profile);

            // TODO asUser.setFollowersCount(?);

            user.broadcastEvent(new UserUpdateEvent(asUser));

            for (ConnectionHolder holder : user.getConnections()) {
                if (holder.getHeldEvent() != null) {
                    if (holder.getHeldEvent() instanceof ViewerListEvent) {
                        ViewerListEvent viewerListEvent = (ViewerListEvent) holder.getHeldEvent();

                        for (User viewer : viewerListEvent.getViewers()) {
                            user.broadcastEvent(new ViewerJoinEvent(viewer, holder.getProfile()));
                        }
                    }

                    user.broadcastEvent(holder.getHeldEvent());
                }
            }
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    @Override
    public synchronized void hook(@NonNull Client user, @NonNull String username) throws IdentifierException {
        try {
            HelixGetUsersRequest request = new HelixGetUsersRequest((TwitchHelixAuth) Koi.getInstance().getAuthProvider(UserPlatform.TWITCH));

            request.addLogin(username);

            HelixUser profile = request.send().get(0);

            user.getConnections().add(getStream(user, profile));

            user.broadcastEvent(new UserUpdateEvent(TwitchUserConverter.transform(profile)));
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    private static ConnectionHolder getMessages(Client user, HelixUser profile) {
        String key = profile.getId() + ":messages";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            holder = new ConnectionHolder(key);

            holder.setProfile(TwitchUserConverter.transform(profile));

            TwitchMessages messages = new TwitchMessages(holder);

            holder.setCloseable(messages);

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getClients().add(user);

        return holder;
    }

    private static ConnectionHolder getFollowers(Client user, HelixUser profile) {
        String key = profile.getId() + ":followers";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            holder = new ConnectionHolder(key);

            holder.setProfile(TwitchUserConverter.transform(profile));
            holder.setCloseable(TwitchWebhookAdapter.hookFollowers(holder));

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getClients().add(user);

        return holder;
    }

    private static ConnectionHolder getStream(Client user, HelixUser profile) {
        String key = profile.getId() + ":stream";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            holder = new ConnectionHolder(key);

            holder.setProfile(TwitchUserConverter.transform(profile));
            holder.setCloseable(TwitchWebhookAdapter.hookStream(holder));

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getClients().add(user);

        return holder;
    }

    private static ConnectionHolder getProfile(Client user, HelixUser profile) {
        String key = profile.getId() + ":profile";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            holder = new ConnectionHolder(key);

            holder.setProfile(TwitchUserConverter.transform(profile));
            holder.setCloseable(TwitchWebhookAdapter.hookProfile(user, holder));

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getClients().add(user);

        return holder;
    }

}
