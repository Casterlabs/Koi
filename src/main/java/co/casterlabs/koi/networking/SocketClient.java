package co.casterlabs.koi.networking;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.java_websocket.WebSocket;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import co.casterlabs.koi.Koi;
import co.casterlabs.koi.events.ChatEvent;
import co.casterlabs.koi.events.DonationEvent;
import co.casterlabs.koi.events.Event;
import co.casterlabs.koi.events.EventListener;
import co.casterlabs.koi.events.FollowEvent;
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.user.IdentifierException;
import co.casterlabs.koi.user.PlatformException;
import co.casterlabs.koi.user.SerializedUser;
import co.casterlabs.koi.user.User;
import co.casterlabs.koi.user.UserPlatform;
import co.casterlabs.koi.user.caffeine.CaffeineUserConverter;
import co.casterlabs.koi.util.CurrencyUtil;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

@RequiredArgsConstructor
public class SocketClient implements EventListener {
    private static final String donationUrl = "https://assets.caffeine.tv/digital-items/wave.58c9cc9c26096f3eb6f74f13603b5515.png";
    private static final JsonObject keepAliveJson = new JsonObject();
    private static final String[] messages = new String[] {
            "I like pancakes",
            "DON'T CLICK THAT!",
            "Have some candy!",
            "I am not the monster you think I am, I am the monster you forced me to be.",
            "MUHAHAHAHAAHAHAH",
            "By the way, I am self-aware."
    };

    private @Getter @NonNull String clientType;
    private @NonNull WebSocket socket;
    private @NonNull Koi koi;

    private @Getter Set<User> users = Collections.synchronizedSet(new HashSet<>());
    private ClientPreferences preferences = new ClientPreferences();

    static {
        keepAliveJson.addProperty("disclaimer", "Made with \u2665 by Casterlabs");
    }

    public void close() {
        for (User user : this.users) {
            user.getEventListeners().remove(this);
        }

        this.users.clear();
    }

    public void sendKeepAlive() {
        if (this.isAlive()) {
            this.send(keepAliveJson, MessageType.KEEP_ALIVE);
        }
    }

    public boolean isAlive() {
        return this.socket.isOpen();
    }

    @Override
    public void onEvent(Event e) {
        this.sendEvent(e);
    }

    public void setPreferences(ClientPreferences preferences) {
        if (preferences != null) {
            this.preferences = preferences;
        }
    }

    public void add(JsonElement username, JsonElement platformJson) {
        try {
            if ((username == null) || username.isJsonNull() || !username.isJsonPrimitive()) {
                this.sendError(RequestError.USER_ID_INVALID);
            } else {
                if (this.users.size() >= 10) {
                    this.sendError(RequestError.USER_LIMIT_REACHED);
                } else {
                    UserPlatform platform = UserPlatform.parse(platformJson, username.getAsString());
                    User user = this.koi.getUser(username.getAsString().split(";")[0], platform);

                    this.users.add(user);

                    user.getEventListeners().add(this);
                    user.tryExternalHook();

                    for (Event e : user.getDataEvents().values()) {
                        this.sendEvent(e);
                    }

                    this.sendEvent(new UserUpdateEvent(user));
                }
            }
        } catch (IdentifierException e) {
            this.sendError(RequestError.USER_ID_INVALID);
        } catch (PlatformException e) {
            this.sendError(RequestError.USER_PLATFORM_INVALID);
        } catch (Exception e) {
            FastLogger.logException(e);
            this.sendError(RequestError.SERVER_API_ERROR);
        }
    }

    public void remove(JsonElement username, JsonElement platformJson) {
        try {
            if ((username == null) || username.isJsonNull() || !username.isJsonPrimitive()) {
                this.sendError(RequestError.USER_ID_INVALID);
            } else {
                UserPlatform platform = UserPlatform.parse(platformJson, username.getAsString());
                User user = this.koi.getUser(username.getAsString().split(";")[0], platform);

                if (this.users.remove(user)) {
                    user.getEventListeners().remove(this);
                } else {
                    this.sendError(RequestError.USER_NOT_PRESENT);
                }
            }
        } catch (IdentifierException e) {
            this.sendError(RequestError.USER_ID_INVALID);
        } catch (PlatformException e) {
            this.sendError(RequestError.USER_PLATFORM_INVALID);
        } catch (Exception e) {
            FastLogger.logException(e);
            this.sendError(RequestError.SERVER_INTERNAL_ERROR);
        }
    }

    public void test(JsonElement test) {
        try {
            User user = this.koi.getUser("Casterlabs", UserPlatform.CAFFEINE);
            SerializedUser casterlabs = CaffeineUserConverter.getInstance().get("Casterlabs");

            switch (test.getAsString().toUpperCase()) {
                case "ALL":
                    this.sendEvent(new DonationEvent("", randomMessage(), casterlabs, user, donationUrl, "USD", 0));
                    this.sendEvent(new ChatEvent("", randomMessage(), casterlabs, user));
                    this.sendEvent(new FollowEvent(casterlabs, user));
                    return;

                case "DONATION":
                    this.sendEvent(new DonationEvent("", randomMessage(), casterlabs, user, donationUrl, "USD", 0));
                    return;

                case "CHAT":
                    this.sendEvent(new ChatEvent("", randomMessage(), casterlabs, user));
                    return;

                case "FOLLOW":
                    this.sendEvent(new FollowEvent(casterlabs, user));
                    return;

                default:
                    this.sendError(RequestError.REQUEST_CRITERIA_INVAID);
                    return;
            }
        } catch (IdentifierException e) {
            this.sendError(RequestError.USER_ID_INVALID);
        } catch (Exception e) {
            FastLogger.logException(e);
            this.sendError(RequestError.SERVER_INTERNAL_ERROR);
        }
    }

    private void send(JsonObject json, MessageType type) {
        json.addProperty("type", type.name());

        if (this.isAlive()) {
            if (this.isAlive()) {
                this.socket.send(json.toString());
            }
        }
    }

    private void sendString(MessageType type, String key, String value) {
        JsonObject json = new JsonObject();

        json.addProperty(key, value);

        this.send(json, type);
    }

    public void sendEvent(Event e) {
        if (e != null) {
            JsonObject json = new JsonObject();

            if (e instanceof DonationEvent) {
                DonationEvent event = (DonationEvent) e;
                JsonObject eventJson = event.serialize();

                try {
                    JsonObject currencyInfo = new JsonObject();

                    double amount = CurrencyUtil.translateCurrencyFromUSD(event.getUsdEquivalent(), this.preferences.getCurrency());
                    String formatted = CurrencyUtil.formatCurrency(amount, this.preferences.getCurrency());

                    currencyInfo.addProperty("amount", amount);
                    currencyInfo.addProperty("formatted", formatted);
                    currencyInfo.addProperty("currency", this.preferences.getCurrency().toUpperCase());

                    eventJson.add("currency_info", currencyInfo);
                } catch (Exception ex) {
                    FastLogger.logStatic("Unable to convert currency to %s", this.preferences.getCurrency());
                    FastLogger.logException(ex);
                }

                json.add("event", eventJson);
            } else {
                json.add("event", e.serialize());
            }

            this.send(json, MessageType.EVENT);
        }
    }

    public void sendServerMessage(String message) {
        this.sendString(MessageType.SERVER, "server", message);
    }

    public void sendError(RequestError error) {
        this.sendString(MessageType.ERROR, "error", error.name());
    }

    public static String randomMessage() {
        return messages[ThreadLocalRandom.current().nextInt(messages.length)];
    }

}
