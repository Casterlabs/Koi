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
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.user.ConnectionHolder;
import co.casterlabs.koi.user.IdentifierException;
import co.casterlabs.koi.user.KoiAuthProvider;
import co.casterlabs.koi.user.User;
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
    public void hookWithAuth(@NonNull User user, @NonNull KoiAuthProvider auth) throws IdentifierException {
        try {
            CaffeineAuth caffeineAuth = (CaffeineAuth) auth;
            String caid = caffeineAuth.getCaid();

            CaffeineUserInfoRequest request = new CaffeineUserInfoRequest();

            request.setCAID(caid);

            CaffeineUser profile = request.send();

            user.getClosables().add(getMessagesConnection(user, profile, caffeineAuth));
            user.getClosables().add(getViewersConnection(user, profile, caffeineAuth));
            user.getClosables().add(getQueryConnection(user, profile));

            user.broadcastEvent(new UserUpdateEvent(CaffeineUserConverter.getInstance().transform(profile)));
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    @Override
    public void hook(@NonNull User user, @NonNull String username) throws IdentifierException {
        try {
            CaffeineUserInfoRequest request = new CaffeineUserInfoRequest();

            request.setUsername(username);

            CaffeineUser profile = request.send();

            user.getClosables().add(getQueryConnection(user, profile));
        } catch (ApiException e) {
            throw new IdentifierException();
        }
    }

    private static ConnectionHolder getMessagesConnection(User user, CaffeineUser profile, CaffeineAuth caffeineAuth) {
        String key = profile.getCAID() + ":messages";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            CaffeineMessages messages = new CaffeineMessages(profile);

            holder = new ConnectionHolder(messages, key);

            holder.setProfile(CaffeineUserConverter.getInstance().transform(profile));

            messages.setAuth(caffeineAuth);
            messages.setListener(new CaffeineMessagesListenerAdapter(messages, holder));
            messages.connect();

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getUsers().add(user);

        return holder;
    }

    private static ConnectionHolder getViewersConnection(User user, CaffeineUser profile, CaffeineAuth caffeineAuth) {
        String key = profile.getCAID() + ":viewers";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            CaffeineViewers viewers = new CaffeineViewers(caffeineAuth);

            holder = new ConnectionHolder(viewers, key);

            holder.setProfile(CaffeineUserConverter.getInstance().transform(profile));

            viewers.setListener(new CaffeineViewersListenerAdapter(viewers, holder));
            viewers.connect();

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getUsers().add(user);

        return holder;
    }

    private static ConnectionHolder getQueryConnection(User user, CaffeineUser profile) {
        String key = profile.getCAID() + ":query";

        ConnectionHolder holder = connectionCache.get(key);

        if (holder == null) {
            CaffeineQuery query = new CaffeineQuery(profile);

            holder = new ConnectionHolder(query, key);

            holder.setProfile(CaffeineUserConverter.getInstance().transform(profile));

            query.setListener(new CaffeineQueryListenerAdapter(query, holder));
            query.connect();

            connectionCache.put(key, holder);
            cache.register(holder);
        }

        holder.getUsers().add(user);

        return holder;
    }

}
