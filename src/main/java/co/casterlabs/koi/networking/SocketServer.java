package co.casterlabs.koi.networking;

import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import co.casterlabs.koi.Koi;
import co.casterlabs.koi.RepeatingThread;
import co.casterlabs.koi.events.ChatEvent;
import co.casterlabs.koi.events.EventType;
import co.casterlabs.koi.networking.incoming.ChatRequest;
import co.casterlabs.koi.networking.incoming.CredentialsRequest;
import co.casterlabs.koi.networking.incoming.DeleteMyDataRequest;
import co.casterlabs.koi.networking.incoming.RequestType;
import co.casterlabs.koi.networking.incoming.TestEventRequest;
import co.casterlabs.koi.networking.incoming.UpvoteRequest;
import co.casterlabs.koi.networking.incoming.UserLoginRequest;
import co.casterlabs.koi.networking.incoming.UserStreamStatusRequest;
import co.casterlabs.koi.networking.outgoing.ClientBannerNotice;
import lombok.Getter;
import lombok.NonNull;
import lombok.SneakyThrows;
import xyz.e3ndr.eventapi.events.AbstractEvent;
import xyz.e3ndr.eventapi.events.deserializer.GsonEventDeserializer;
import xyz.e3ndr.eventapi.listeners.EventWrapper;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class SocketServer extends WebSocketServer implements Server {
    public static final long KEEP_ALIVE_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private static GsonEventDeserializer<RequestType> eventDeserializer = new GsonEventDeserializer<>();

    private static @Getter SocketServer instance;

    private RepeatingThread thread = new RepeatingThread("Keep Alive - Koi", KEEP_ALIVE_INTERVAL, () -> this.keepAllAlive());
    private @Getter boolean running = false;
    private Koi koi;

    static {
        eventDeserializer.registerEventClass(RequestType.USER_STREAM_STATUS, UserStreamStatusRequest.class);
        eventDeserializer.registerEventClass(RequestType.LOGIN, UserLoginRequest.class);
        eventDeserializer.registerEventClass(RequestType.TEST, TestEventRequest.class);
        eventDeserializer.registerEventClass(RequestType.CREDENTIALS, CredentialsRequest.class);
        eventDeserializer.registerEventClass(RequestType.UPVOTE, UpvoteRequest.class);
        eventDeserializer.registerEventClass(RequestType.CHAT, ChatRequest.class);
        eventDeserializer.registerEventClass(RequestType.DELETE_MY_DATA, DeleteMyDataRequest.class);
    }

    public SocketServer(InetSocketAddress bind, Koi koi) {
        super(bind);

        if (instance == null) {
            instance = this;
        }

        this.koi = koi;
    }

    private void keepAllAlive() {
        if (this.running) {
            long current = System.currentTimeMillis();

            for (WebSocket conn : this.getConnections()) {
                SocketClient client = conn.getAttachment();

                if (client.isExpired(current)) {
                    client.sendError(RequestError.FAILED_KEEP_ALIVE, null);
                    client.onClose();
                    conn.close();
                } else {
                    client.sendKeepAlive();
                }
            }
        } else {
            this.thread.stop();
        }
    }

    @SneakyThrows
    @Override
    public void stop() {
        this.running = false;
        super.stop();
    }

    @Override
    public void start() {
        super.start();

        Koi.getInstance().getLogger().info("Koi started on %s:%d!", this.getAddress().getHostString(), this.getPort());
    }

    @Override
    public void onStart() {
        this.running = true;
        this.thread.start();
    }

    public void systemBroadcast(@NonNull String message) {
        ChatEvent event = new ChatEvent("-1", message, EventType.getSystemUser(), EventType.getSystemUser());

        for (WebSocket conn : this.getConnections()) {
            SocketClient client = conn.getAttachment();

            client.sendEvent(event);
            client.sendSystemMessage(message);
        }
    }

    public void sendNotices() {
        ClientBannerNotice[] notices = Koi.getInstance().getNotices();

        for (WebSocket conn : this.getConnections()) {
            SocketClient client = conn.getAttachment();

            for (ClientBannerNotice notice : notices) {
                client.sendNotice(notice);
            }
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        SocketClient client = conn.getAttachment();

        client.onClose();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        String clientType = handshake.getFieldValue("User-Agent");

        if (clientType == null) {
            clientType = "UNKNOWN";
        } else if (clientType.contains(" Caffeinated/")) {
            clientType = "Caffeinated";
        }

        SocketClient client = new SocketClient(clientType, conn, this.koi);

        conn.setAttachment(client);

        client.sendWelcomeMessage();
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        SocketClient client = conn.getAttachment();

        Koi.getClientThreadPool().submit(() -> {
            try {
                JsonObject json = Koi.GSON.fromJson(message, JsonObject.class);
                RequestType type = GsonEventDeserializer.parseEnumFromJsonElement(RequestType.values(), json.get("type"));

                if (type == RequestType.KEEP_ALIVE) {
                    client.onPong();
                } else {
                    AbstractEvent<RequestType> request = eventDeserializer.deserializeJson(type, json);

                    for (EventWrapper wrapper : client.getWrappers()) {
                        try {
                            wrapper.call(request);
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    }
                }
            } catch (JsonParseException e) {
                client.sendError(RequestError.REQUEST_JSON_INVAID, null);
            } catch (IllegalArgumentException e) {
                client.sendError(RequestError.REQUEST_TYPE_INVAID, null);
            } catch (NullPointerException e) {
                client.sendError(RequestError.REQUEST_CRITERIA_INVAID, null);
            } catch (Throwable e) {
                client.sendError(RequestError.SERVER_INTERNAL_ERROR, null);
            }
        });
    }

    @Override
    public void onError(WebSocket conn, Exception e) {
        FastLogger.logException(e);
    }

}
