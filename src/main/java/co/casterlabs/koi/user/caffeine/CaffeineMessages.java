package co.casterlabs.koi.user.caffeine;

import java.net.SocketException;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import co.casterlabs.koi.Koi;
import co.casterlabs.koi.RepeatingThread;
import co.casterlabs.koi.events.ChatEvent;
import co.casterlabs.koi.events.DonationEvent;
import co.casterlabs.koi.events.UpvoteEvent;
import co.casterlabs.koi.user.SerializedUser;
import lombok.SneakyThrows;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class CaffeineMessages extends WebSocketClient {
    private static final String LOGIN_HEADER = "{\"Headers\":{\"Authorization\":\"Anonymous Fish\",\"X-Client-Type\":\"api\"}}";
    private static final long CAFFEINE_KEEPALIVE = TimeUnit.SECONDS.toMillis(15);

    private CaffeineUser user;
    private RepeatingThread keepAlive = new RepeatingThread("Keep Alive - Caffeine", CAFFEINE_KEEPALIVE, () -> {
        try {
            if (!this.isOpen()) {
                this.keepAlive.stop();
            } else if (this.user.hasListeners()) {
                this.send("\"HEALZ\"");
            } else {
                this.closeBlocking();
                this.keepAlive.stop();
            }
        } catch (InterruptedException e) {}
    });

    @SneakyThrows
    public CaffeineMessages(CaffeineUser user) {
        super(CaffeineLinks.getMessagesLink(user.getUUID().substring(4)));

        this.user = user;
        this.connect();
    }

    @Override
    public void onOpen(ServerHandshake handshakedata) {
        this.send(LOGIN_HEADER);
        this.keepAlive.start();
    }

    @Override
    public void onMessage(String message) {
        try {
            if (!message.equals("\"THANKS\"")) {
                JsonObject json = Koi.GSON.fromJson(message, JsonObject.class);

                if (!json.has("Compatibility-Mode")) {
                    JsonObject publisher = json.getAsJsonObject("publisher");
                    JsonObject body = json.getAsJsonObject("body");
                    CaffeineAlertType type = CaffeineAlertType.valueOfString(json.get("type").getAsString().toUpperCase());

                    if (type != null) {
                        SerializedUser sender = CaffeineUserConverter.getInstance().transform(publisher);
                        ChatEvent event = null;
                        String id = getId(json.get("id").getAsString());

                        switch (type) {
                            case SHARE:
                                event = new ChatEvent(id, body.get("text").getAsString(), sender, this.user);
                                break;

                            case REACTION:
                                event = new ChatEvent(id, body.get("text").getAsString(), sender, this.user);
                                break;

                            case DIGITAL_ITEM:
                                JsonObject donation = body.getAsJsonObject("digital_item");
                                String image = CaffeineLinks.getImageLink(donation.get("static_image_path").getAsString());
                                int amount = donation.get("count").getAsInt() * donation.get("credits_per_item").getAsInt();

                                event = new DonationEvent(id, body.get("text").getAsString(), sender, this.user, image, "DIGIES", amount);
                                break;

                            case UNKNOWN:
                                FastLogger.logStatic(LogLevel.DEBUG, json.toString());
                                return;
                        }

                        if (json.has("endorsement_count")) {
                            this.user.broadcastEvent(new UpvoteEvent(event, json.get("endorsement_count").getAsInt()));
                        } else {
                            this.user.broadcastEvent(event);
                        }
                    }
                }
            }
        } catch (JsonSyntaxException ignored) {
            // Ignore, message is most likely `FAILED KEEPALIVE`
        } catch (Exception e) {
            FastLogger.logException(e); // Prevents the socket from closing.
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        this.keepAlive.stop();

        if (this.user.hasListeners()) {
            Koi.getMiscThreadPool().submit(() -> this.reconnect());
        }
    }

    @Override
    public void onError(Exception e) {
        if (!(e instanceof SocketException)) { // Java WS lib produces this error randomly.
            FastLogger.logStatic("Uncaught exception:");
            FastLogger.logException(e);
        }
    }

    private static String getId(String b64) {
        byte[] bytes = Base64.getDecoder().decode(b64);
        JsonObject json = Koi.GSON.fromJson(new String(bytes), JsonObject.class);

        return json.get("u").getAsString();
    }

}
