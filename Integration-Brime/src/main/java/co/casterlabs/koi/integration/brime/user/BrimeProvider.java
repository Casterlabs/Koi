package co.casterlabs.koi.integration.brime.user;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

import co.casterlabs.apiutil.auth.ApiAuthException;
import co.casterlabs.apiutil.web.ApiException;
import co.casterlabs.brimeapijava.realtime.BrimeRealtime;
import co.casterlabs.brimeapijava.requests.BrimeGetChannelRequest;
import co.casterlabs.brimeapijava.requests.BrimeGetStreamRequest;
import co.casterlabs.brimeapijava.requests.BrimeGetUserRequest;
import co.casterlabs.brimeapijava.requests.BrimeSendChatMessageRequest;
import co.casterlabs.brimeapijava.types.BrimeChannel;
import co.casterlabs.brimeapijava.types.BrimeStream;
import co.casterlabs.brimeapijava.types.BrimeUser;
import co.casterlabs.koi.client.Client;
import co.casterlabs.koi.client.ClientAuthProvider;
import co.casterlabs.koi.client.ConnectionHolder;
import co.casterlabs.koi.events.StreamStatusEvent;
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.integration.brime.BrimeIntegration;
import co.casterlabs.koi.user.IdentifierException;
import co.casterlabs.koi.user.User;
import co.casterlabs.koi.user.UserProvider;
import co.casterlabs.koi.util.RepeatingThread;
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

            // Very dirty, very naughty.
            asUser.setUUID(brimeAuth.getUUID());

            client.setProfile(asUser);
            client.setSimpleProfile(asUser.getSimpleProfile());

            client.getConnections().add(getRealtimeConnection(client, brimeAuth));
            client.getConnections().add(getProfileUpdater(client, brimeAuth));
            client.getConnections().add(getStreamPoller(client, asUser.getUsername()));

            client.broadcastEvent(new UserUpdateEvent(asUser));
        } catch (Exception e) {
            throw new IdentifierException();
        }
    }

    @Override
    public void hook(@NonNull Client client, @NonNull String username) throws IdentifierException {
        try {
            User asUser = BrimeUserConverter.getInstance().get(username);

            client.setProfile(asUser);
            client.setSimpleProfile(asUser.getSimpleProfile());

            client.getConnections().add(getStreamPoller(client, username));

            client.broadcastEvent(new UserUpdateEvent(asUser));
        } catch (Exception e) {
            throw new IdentifierException();
        }
    }

    @Override
    public void chat(Client client, @NonNull String message, ClientAuthProvider auth) throws ApiAuthException {
        //@formatter:off
        try {
            new BrimeSendChatMessageRequest((BrimeUserAuth) auth)
                    .setChannelId(client.getUUID())
                    .setColor("#ea4c4c")
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
    private static ConnectionHolder getRealtimeConnection(Client client, BrimeUserAuth brimeAuth) throws AblyException {
        String key = brimeAuth.getUUID() + ":realtime";

        ConnectionHolder holder = (ConnectionHolder) connectionCache.getItemById(key);

        if (holder == null) {
            BrimeRealtime realtime = new BrimeRealtime(BrimeIntegration.getInstance().getAblySecret(), brimeAuth.getChannelName().toLowerCase());

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

        ConnectionHolder holder = new ConnectionHolder(key, client.getSimpleProfile());
        RepeatingThread thread = new RepeatingThread("Brime profile updater " + brimeAuth.getUUID(), TimeUnit.MINUTES.toMillis(2), () -> {
            try {
                User asUser = getProfile(brimeAuth);

                brimeAuth.refresh();

                client.broadcastEvent(new UserUpdateEvent(asUser));
            } catch (ApiException e) {
                client.notifyCredentialExpired();
            }
        });

        holder.getClients().add(client);

        holder.setCloseable(thread);

        thread.start();

        return holder;
    }

    private static ConnectionHolder getStreamPoller(Client client, String username) {
        String key = username + ":stream";

        ConnectionHolder holder = (ConnectionHolder) connectionCache.getItemById(key);

        if (holder == null) {
            holder = new ConnectionHolder(key, client.getSimpleProfile());

            ConnectionHolder pointer = holder;

            RepeatingThread thread = new RepeatingThread("Brime stream poller " + username, TimeUnit.MINUTES.toMillis(1), new Runnable() {
                private Instant streamStartedAt;

                private String title = "";

                @Override
                public void run() {
                    try {
                        //@formatter:off
                        BrimeStream stream = new BrimeGetStreamRequest(BrimeIntegration.getInstance().getAppAuth())
                                .setChannel(username)
                                .send();
                        //@formatter:on

                        boolean isLive = stream.isLive();

                        this.title = stream.getTitle();

                        if (isLive) {
                            if (this.streamStartedAt == null) {
                                this.streamStartedAt = Instant.now();
                            }
                        } else {
                            this.streamStartedAt = null;
                        }

                        StreamStatusEvent e = new StreamStatusEvent(isLive, this.title, pointer.getProfile(), this.streamStartedAt);

                        pointer.broadcastEvent(e);
                        pointer.setHeldEvent(e);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });

            holder.getClients().add(client);

            holder.setCloseable(thread);

            thread.start();

            connectionCache.registerItem(key, holder);
        } else {
            holder.getClients().add(client);
        }

        return holder;
    }

    /**
     * @param  brimeAuth
     * 
     * @return
     * 
     * @throws ApiAuthException
     * @throws ApiException
     */
    private static User getProfile(BrimeUserAuth brimeAuth) throws ApiAuthException, ApiException {
        //@formatter:off
        BrimeChannel channel = new BrimeGetChannelRequest(brimeAuth)
                                .setChannel("me")
                                .send();
        
        BrimeUser user = new BrimeGetUserRequest(brimeAuth)
                                .setName("me")
                                .send();
        //@formatter:on

        User asUser = BrimeUserConverter.getInstance().transform(user);

        asUser.setFollowersCount(channel.getFollowerCount());
        asUser.setSubCount(channel.getSubscriberCount());

        return asUser;
    }

}
