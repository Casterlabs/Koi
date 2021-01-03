package co.casterlabs.koi.user;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.jetbrains.annotations.Nullable;

import co.casterlabs.koi.events.Event;
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.user.caffeine.CaffeineProvider;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.watercache.cachable.Cachable;
import xyz.e3ndr.watercache.cachable.DisposeReason;

public class ConnectionHolder extends Cachable {
    private @Setter Closeable closeable;
    private String key;

    private @Getter Set<Client> clients = new HashSet<>();
    private @Getter User profile;

    private @Getter boolean expired = false;
    private @Getter FastLogger logger;

    private @Getter @Setter @Nullable Event heldEvent;

    public ConnectionHolder(@NonNull String key) {
        super(TimeUnit.MINUTES, 1);

        this.key = key;

        this.logger = new FastLogger(this.key);

        this.logger.debug("Created connection");
    }

    public void broadcastEvent(Event e) {
        for (Client user : new ArrayList<>(this.clients)) {
            user.broadcastEvent(e);
        }
    }

    public void setProfile(@NonNull User profile) {
        this.profile = profile;

        this.broadcastEvent(new UserUpdateEvent(this.profile));
    }

    @Override
    public boolean onDispose(DisposeReason reason) {
        if (this.clients.size() > 0) {
            this.life += TimeUnit.MINUTES.toMillis(1);

            return false;
        } else {
            this.expired = true;

            try {
                this.closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            this.logger.debug("Removed self from connection cache.");

            CaffeineProvider.getConnectionCache().remove(this.key);

            return true;
        }
    }

}
