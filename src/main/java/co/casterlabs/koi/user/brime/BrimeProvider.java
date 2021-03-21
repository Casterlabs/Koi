package co.casterlabs.koi.user.brime;

import java.util.concurrent.TimeUnit;

import co.casterlabs.apiutil.auth.ApiAuthException;
import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.brimeapijava.realtime.BrimeRealtime;
import co.casterlabs.brimeapijava.requests.BrimeGetChannelFollowerCountRequest;
import co.casterlabs.brimeapijava.requests.BrimeSendChatMessageRequest;
import co.casterlabs.koi.Koi;
import co.casterlabs.koi.RepeatingThread;
import co.casterlabs.koi.client.Client;
import co.casterlabs.koi.client.ClientAuthProvider;
import co.casterlabs.koi.client.ConnectionHolder;
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.user.IdentifierException;
import co.casterlabs.koi.user.User;
import co.casterlabs.koi.user.UserProvider;
import io.ably.lib.types.AblyException;
import lombok.NonNull;
import xyz.e3ndr.watercache.WaterCache;

public class BrimeProvider implements UserProvider {
    private static WaterCache connectionCache = new WaterCache();

    static {
        connectionCache.start(TimeUnit.MINUTES, 1);
    }

    @Override
    public void hookWithAuth(@NonNull Client client, @NonNull ClientAuthProvider auth) throws IdentifierException {
        try {
            BrimeUserAuth brimeAuth = (BrimeUserAuth) auth;

            User asUser = getProfile(brimeAuth);

            client.setProfile(asUser);
            client.setSimpleProfile(asUser.getSimpleProfile());

            client.getConnections().add(getChatConnection(client, brimeAuth));
            client.getConnections().add(getProfileUpdater(client, brimeAuth));

            client.broadcastEvent(new UserUpdateEvent(asUser));
        } catch (Exception e) {
            throw new IdentifierException();
        }
    }

    @Override
    public void hook(@NonNull Client client, @NonNull String username) throws IdentifierException {
        throw new IdentifierException();
    }

    @Override
    public void chat(Client client, @NonNull String message, ClientAuthProvider auth) throws ApiAuthException {
        BrimeUserAuth brimeAuth = (BrimeUserAuth) auth;

        //@formatter:off
        try {
            new BrimeSendChatMessageRequest(brimeAuth.getToken())
            .setChannel(client.getProfile().getUsername())
            .setColor("#ea4c4c")
            .setUsername(brimeAuth.getUsername())
            .setMessage(message)
            .send();
        } catch (ApiAuthException e) {
            throw e;
        } catch (ApiException e) {
            e.printStackTrace();
        }
        //@formatter:on
    }

    @SuppressWarnings("deprecation")
    private static ConnectionHolder getChatConnection(Client client, BrimeUserAuth brimeAuth) throws AblyException {
        String key = brimeAuth.getUUID() + ":realtime";

        ConnectionHolder holder = (ConnectionHolder) connectionCache.getItemById(key);

        if (holder == null) {
            BrimeRealtime realtime = new BrimeRealtime(Koi.getInstance().getConfig().getBrimeAblySecret(), brimeAuth.getUsername().toLowerCase());

            holder = new ConnectionHolder(key, client.getSimpleProfile());

            holder.getClients().add(client);

            holder.setCloseable(realtime);

            realtime.setListener(new BrimeRealtimeAdapter(holder, realtime));
            realtime.connect();

            connectionCache.registerItem(key, holder);
        } else {
            holder.getClients().add(client);
        }

        return holder;
    }

    private static ConnectionHolder getProfileUpdater(Client client, BrimeUserAuth brimeAuth) {
        String key = brimeAuth.getUUID() + ":profile";

        ConnectionHolder holder = (ConnectionHolder) connectionCache.getItemById(key);

        if (holder == null) {
            RepeatingThread thread = new RepeatingThread("Brime profile updater " + brimeAuth.getUsername(), TimeUnit.MINUTES.toMillis(1), () -> {
                try {
                    User asUser = getProfile(brimeAuth);

                    client.broadcastEvent(new UserUpdateEvent(asUser));
                } catch (ApiException e) {
                    client.notifyCredentialExpired();
                }
            });

            holder = new ConnectionHolder(key, client.getSimpleProfile());

            holder.getClients().add(client);

            holder.setCloseable(thread);

            thread.start();

            connectionCache.registerItem(key, holder);
        } else {
            holder.getClients().add(client);
        }

        return holder;
    }

    private static User getProfile(BrimeUserAuth brimeAuth) throws ApiAuthException, ApiException {
        User asUser = BrimeUserConverter.getInstance().get(brimeAuth.getUsername());

        int followersCount = new BrimeGetChannelFollowerCountRequest(brimeAuth.getUsername().toLowerCase()).send();

        asUser.setFollowersCount(followersCount);

        return asUser;
    }

}
