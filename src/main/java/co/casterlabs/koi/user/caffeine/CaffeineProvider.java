package co.casterlabs.koi.user.caffeine;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.caffeineapi.realtime.messages.CaffeineMessages;
import co.casterlabs.caffeineapi.realtime.query.CaffeineQuery;
import co.casterlabs.caffeineapi.realtime.viewers.CaffeineViewers;
import co.casterlabs.caffeineapi.requests.CaffeineUser;
import co.casterlabs.caffeineapi.requests.CaffeineUserInfoRequest;
import co.casterlabs.koi.RepeatingThread;
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.user.ConnectionHolder;
import co.casterlabs.koi.user.IdentifierException;
import co.casterlabs.koi.user.KoiAuthProvider;
import co.casterlabs.koi.user.User;
import co.casterlabs.koi.user.UserConnection;
import co.casterlabs.koi.user.UserProvider;
import lombok.Getter;
import lombok.NonNull;
import xyz.e3ndr.watercache.WaterCache;

public class CaffeineProvider implements UserProvider {
    private static @Getter Map<String, ConnectionHolder> connectionCache = new ConcurrentHashMap<>();
    private static WaterCache cache = new WaterCache();

    static {
        cache.start(TimeUnit.MINUTES, 1);
    }

    @Override
    public void hookWithAuth(@NonNull UserConnection user, @NonNull KoiAuthProvider auth) throws IdentifierException {
        try {
            CaffeineAuth caffeineAuth = (CaffeineAuth) auth;
            String caid = caffeineAuth.getCaid();

            CaffeineUserInfoRequest request = new CaffeineUserInfoRequest();

            request.setCAID(caid);

            CaffeineUser profile = request.send();

            System.out.println(connectionCache);

            user.getClosables().add(getProfileUpdater(user, profile, caffeineAuth));
            user.getClosables().add(getMessagesConnection(user, profile, caffeineAuth));
            user.getClosables().add(getViewersConnection(user, profile, caffeineAuth));
            user.getClosables().add(getQueryConnection(user, profile));

            user.broadcastEvent(new UserUpdateEvent(CaffeineUserConverter.getInstance().transform(profile)));

            for (ConnectionHolder holder : user.getClosables()) {
                if (holder.getHeldEvent() != null) {
                    user.broadcastEvent(holder.getHeldEvent());
                }
            }
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    @Override
    public void hook(@NonNull UserConnection user, @NonNull String username) throws IdentifierException {
        try {
            CaffeineUserInfoRequest request = new CaffeineUserInfoRequest();

            request.setUsername(username);

            CaffeineUser profile = request.send();

            user.getClosables().add(getQueryConnection(user, profile));

            user.broadcastEvent(new UserUpdateEvent(CaffeineUserConverter.getInstance().transform(profile)));
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    private static ConnectionHolder getProfileUpdater(UserConnection user, CaffeineUser profile, CaffeineAuth caffeineAuth) {
        String key = profile.getCAID() + ":profile";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            RepeatingThread thread = new RepeatingThread("Caffeine profile updater " + profile.getCAID(), TimeUnit.MINUTES.toMillis(5), () -> {
                try {
                    CaffeineUserInfoRequest request = new CaffeineUserInfoRequest();

                    request.setCAID(profile.getCAID());

                    CaffeineUser updatedProfile = request.send();

                    User result = CaffeineUserConverter.getInstance().transform(updatedProfile);

                    result.setFollowersCount(updatedProfile.getFollowersCount());

                    user.updateProfileSafe(result);
                } catch (ApiException ignored) {}
            });

            holder = new ConnectionHolder(key);

            holder.setProfile(CaffeineUserConverter.getInstance().transform(profile));
            holder.setCloseable(thread);

            thread.start();

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getUsers().add(user);

        return holder;
    }

    private static ConnectionHolder getMessagesConnection(UserConnection user, CaffeineUser profile, CaffeineAuth caffeineAuth) {
        String key = profile.getCAID() + ":messages";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            CaffeineMessages messages = new CaffeineMessages(profile);

            holder = new ConnectionHolder(key);

            holder.setProfile(CaffeineUserConverter.getInstance().transform(profile));
            holder.setCloseable(messages);

            messages.setAuth(caffeineAuth);
            messages.setListener(new CaffeineMessagesListenerAdapter(messages, holder));
            messages.connect();

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getUsers().add(user);

        return holder;
    }

    private static ConnectionHolder getViewersConnection(UserConnection user, CaffeineUser profile, CaffeineAuth caffeineAuth) {
        String key = profile.getCAID() + ":viewers";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            CaffeineViewers viewers = new CaffeineViewers(caffeineAuth);

            holder = new ConnectionHolder(key);

            holder.setProfile(CaffeineUserConverter.getInstance().transform(profile));
            holder.setCloseable(viewers);

            viewers.setListener(new CaffeineViewersListenerAdapter(viewers, holder));
            viewers.connect();

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getUsers().add(user);

        return holder;
    }

    private static ConnectionHolder getQueryConnection(UserConnection user, CaffeineUser profile) {
        String key = profile.getCAID() + ":query";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            CaffeineQuery query = new CaffeineQuery(profile);

            holder = new ConnectionHolder(key);

            holder.setProfile(CaffeineUserConverter.getInstance().transform(profile));
            holder.setCloseable(query);

            query.setListener(new CaffeineQueryListenerAdapter(query, holder));
            query.connect();

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getUsers().add(user);

        return holder;
    }

}
