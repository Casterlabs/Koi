package co.casterlabs.koi.user;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import co.casterlabs.koi.events.Event;
import co.casterlabs.koi.events.UserUpdateEvent;
import co.casterlabs.koi.user.caffeine.CaffeineProvider;
import lombok.Getter;
import lombok.NonNull;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;
import xyz.e3ndr.watercache.cachable.Cachable;
import xyz.e3ndr.watercache.cachable.DisposeReason;

public class ConnectionHolder extends Cachable {
    private Closeable closeable;
    private String key;

    private @Getter List<User> users = new ArrayList<>();
    private @Getter SerializedUser profile;

    public ConnectionHolder(@NonNull Closeable closeable, @NonNull String key) {
        super(TimeUnit.MINUTES, 1);

        this.closeable = closeable;
        this.key = key;
    }

    public void broadcastEvent(Event e) {
        for (User user : new ArrayList<>(this.users)) {
            user.broadcastEvent(e);
        }
    }

    public void setProfile(@NonNull SerializedUser profile) {
        this.profile = profile;

        this.broadcastEvent(new UserUpdateEvent(this.profile));
    }

    @Override
    public boolean onDispose(DisposeReason reason) {
        if (this.users.size() > 0) {
            this.life += TimeUnit.MINUTES.toMillis(5);

            return false;
        } else {
            try {
                this.closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            CaffeineProvider.getConnectionCache().remove(this.key);

            FastLogger.logStatic(LogLevel.DEBUG, "Removed %s from the connection cache.", this.key);

            return true;
        }
    }

}
