package co.casterlabs.koi.client;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;

import co.casterlabs.koi.Natsukashii;
import co.casterlabs.koi.Natsukashii.AuthException;
import co.casterlabs.koi.RepeatingThread;
import co.casterlabs.koi.events.Event;
import co.casterlabs.koi.events.ViewerJoinEvent;
import co.casterlabs.koi.events.ViewerListEvent;
import co.casterlabs.koi.user.IdentifierException;
import co.casterlabs.koi.user.PlatformException;
import co.casterlabs.koi.user.User;
import co.casterlabs.koi.user.UserPlatform;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public class Client {
    private @Getter List<ConnectionHolder> connections = new ArrayList<>();
    private @Nullable @Getter ClientAuthProvider auth;

    private @Getter @Setter SimpleProfile simpleProfile;
    private @Getter @Setter User profile;

    private ClientEventListener listener;
    private String token;

    private RepeatingThread authValidator = new RepeatingThread("Client auth validator", TimeUnit.MINUTES.toMillis(5), () -> {
        if (!this.auth.isValid()) {
            this.notifyCredentialExpired();
        }
    });

    public Client(@NonNull ClientEventListener listener, @NonNull String token) throws IdentifierException, PlatformException {
        try {
            this.auth = Natsukashii.get(token);

            if (this.auth.getPlatform().isEnabled()) {
                this.listener = listener;
                this.token = token;

                this.authValidator.start();

                this.auth.getPlatform().getProvider().hookWithAuth(this, this.auth);

                for (ConnectionHolder holder : this.connections) {
                    if (holder.getHeldEvent() != null) {
                        if (holder.getHeldEvent() instanceof ViewerListEvent) {
                            ViewerListEvent viewerListEvent = (ViewerListEvent) holder.getHeldEvent();

                            for (User viewer : viewerListEvent.getViewers()) {
                                this.broadcastEvent(new ViewerJoinEvent(viewer, this.profile));
                            }
                        }

                        this.broadcastEvent(holder.getHeldEvent());
                    }
                }
            } else {
                throw new PlatformException();
            }
        } catch (AuthException e) {
            FastLogger.logStatic(LogLevel.DEBUG, e);
            throw new IdentifierException();
        }
    }

    public Client(@NonNull ClientEventListener listener, @NonNull String username, @NonNull UserPlatform platform) throws IdentifierException, PlatformException {
        if (platform.isEnabled()) {
            this.listener = listener;

            platform.getProvider().hook(this, username);

            for (ConnectionHolder holder : this.connections) {
                if (holder.getHeldEvent() != null) {
                    this.broadcastEvent(holder.getHeldEvent());
                }
            }
        } else {
            throw new PlatformException();
        }
    }

    public void broadcastEvent(@NonNull Event e) {
        this.listener.onEvent(e);
    }

    public void upvote(@NonNull String id) throws UnsupportedOperationException {
        this.auth.getPlatform().getProvider().upvote(this, id, this.auth);
    }

    public void chat(@NonNull String message) {
        this.auth.getPlatform().getProvider().chat(this, message, this.auth);
    }

    public JsonElement getCredentials() {
        if (this.auth != null) {
            if (this.auth.isValid()) {
                return this.auth.getCredentials();
            } else {
                this.notifyCredentialExpired();
            }
        }

        return JsonNull.INSTANCE;
    }

    public void notifyCredentialExpired() {
        if (this.token != null) {
            Natsukashii.revoke(this.token);
        }

        this.listener.onCredentialExpired();
    }

    public void close() {
        this.authValidator.stop();

        for (ConnectionHolder closeable : this.connections) {
            closeable.getClients().remove(this);
        }
    }

    public String getUUID() {
        return this.profile.getUUID();
    }

}
