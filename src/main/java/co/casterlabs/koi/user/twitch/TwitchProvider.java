package co.casterlabs.koi.user.twitch;

import java.util.concurrent.TimeUnit;

import co.casterlabs.apiutil.auth.ApiAuthException;
import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.koi.Koi;
import co.casterlabs.koi.RepeatingThread;
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
import co.casterlabs.twitchapi.helix.HelixGetUserFollowersRequest;
import co.casterlabs.twitchapi.helix.HelixGetUsersRequest;
import co.casterlabs.twitchapi.helix.HelixGetUsersRequest.HelixUser;
import co.casterlabs.twitchapi.helix.TwitchHelixAuth;
import lombok.NonNull;
import xyz.e3ndr.watercache.WaterCache;

public class TwitchProvider implements UserProvider {
    private static WaterCache cache = new WaterCache();

    static {
        cache.start(TimeUnit.MINUTES, 1);
    }

    @Override
    public synchronized void hookWithAuth(@NonNull Client client, @NonNull KoiAuthProvider auth) throws IdentifierException {
        try {
            TwitchTokenAuth twitchAuth = (TwitchTokenAuth) auth;

            HelixGetUsersRequest request = new HelixGetUsersRequest(twitchAuth);

            HelixUser profile = request.send().get(0);

            client.getConnections().add(getMessages(client, profile));
            client.getConnections().add(getFollowers(client, profile));
            client.getConnections().add(getStream(client, profile));
            client.getConnections().add(getProfile(client, profile, twitchAuth));

            User asUser = TwitchUserConverter.transform(profile);

            asUser.setFollowersCount(getFollowersCount(profile.getId(), twitchAuth));

            client.broadcastEvent(new UserUpdateEvent(asUser));

            for (ConnectionHolder holder : client.getConnections()) {
                if (holder.getHeldEvent() != null) {
                    if (holder.getHeldEvent() instanceof ViewerListEvent) {
                        ViewerListEvent viewerListEvent = (ViewerListEvent) holder.getHeldEvent();

                        for (User viewer : viewerListEvent.getViewers()) {
                            client.broadcastEvent(new ViewerJoinEvent(viewer, holder.getProfile()));
                        }
                    }

                    client.broadcastEvent(holder.getHeldEvent());
                }
            }
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    @Override
    public synchronized void hook(@NonNull Client client, @NonNull String username) throws IdentifierException {
        try {
            HelixGetUsersRequest request = new HelixGetUsersRequest((TwitchHelixAuth) Koi.getInstance().getAuthProvider(UserPlatform.TWITCH));

            request.addLogin(username);

            HelixUser profile = request.send().get(0);

            client.getConnections().add(getStream(client, profile));

            client.broadcastEvent(new UserUpdateEvent(TwitchUserConverter.transform(profile)));
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    private static ConnectionHolder getMessages(Client client, HelixUser profile) {
        String key = profile.getId() + ":messages";

        ConnectionHolder holder = (ConnectionHolder) cache.getItemById(key);

        if (holder == null) {
            holder = new ConnectionHolder(key);

            holder.setProfile(TwitchUserConverter.transform(profile));

            TwitchMessages messages = new TwitchMessages(holder);

            holder.setCloseable(messages);

            cache.registerItem(key, holder);
        }

        holder.getClients().add(client);

        return holder;
    }

    private static ConnectionHolder getFollowers(Client client, HelixUser profile) {
        String key = profile.getId() + ":followers";

        ConnectionHolder holder = (ConnectionHolder) cache.getItemById(key);

        if (holder == null) {
            holder = new ConnectionHolder(key);

            holder.setProfile(TwitchUserConverter.transform(profile));
            holder.setCloseable(TwitchWebhookAdapter.hookFollowers(holder));

            cache.registerItem(key, holder);
        }

        holder.getClients().add(client);

        return holder;
    }

    private static ConnectionHolder getStream(Client client, HelixUser profile) {
        String key = profile.getId() + ":stream";

        ConnectionHolder holder = (ConnectionHolder) cache.getItemById(key);

        if (holder == null) {
            holder = new ConnectionHolder(key);

            holder.setProfile(TwitchUserConverter.transform(profile));
            holder.setCloseable(TwitchWebhookAdapter.hookStream(holder));

            cache.registerItem(key, holder);
        }

        holder.getClients().add(client);

        return holder;
    }

    private static ConnectionHolder getProfile(Client client, HelixUser oldProfile, TwitchHelixAuth twitchAuth) {
        ConnectionHolder holder = new ConnectionHolder("Twitch profile updater " + oldProfile.getId());

        RepeatingThread thread = new RepeatingThread("Twitch profile updater " + oldProfile.getId(), TimeUnit.MINUTES.toMillis(2), () -> {
            try {
                HelixGetUsersRequest request = new HelixGetUsersRequest(twitchAuth);

                HelixUser profile = request.send().get(0);
                User user = TwitchUserConverter.transform(profile);

                user.setFollowersCount(getFollowersCount(profile.getId(), twitchAuth));

                client.broadcastEvent(new UserUpdateEvent(user));
            } catch (ApiAuthException e) {
                client.onCredentialExpired();
            } catch (ApiException e) {
                e.printStackTrace();
            }
        });

        holder.setProfile(TwitchUserConverter.transform(oldProfile));
        holder.setCloseable(thread);

        thread.start();

        holder.getClients().add(client);

        return holder;
    }

    public static long getFollowersCount(String id, TwitchHelixAuth twitchAuth) throws ApiAuthException, ApiException {

        HelixGetUserFollowersRequest followersRequest = new HelixGetUserFollowersRequest(id, twitchAuth);

        followersRequest.setFirst(1);

        return followersRequest.send().getTotal();
    }

}
