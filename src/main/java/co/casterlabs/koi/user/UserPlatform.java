package co.casterlabs.koi.user;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import co.casterlabs.koi.Koi;
import co.casterlabs.koi.RepeatingThread;
import co.casterlabs.koi.user.caffeine.CaffeineUser;
import co.casterlabs.koi.user.twitch.TwitchUser;
import co.casterlabs.koi.util.FileUtil;
import lombok.Getter;
import lombok.SneakyThrows;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;
import xyz.e3ndr.fastloggingframework.logging.LogLevel;

public enum UserPlatform {
    CAFFEINE,
    TWITCH;

    private static final long REMOVE_AGE = TimeUnit.MINUTES.toMillis(5);
    private static final File USERNAMES = new File("usernames.json");
    private static final File STATS = new File("stats.json");
    public static final long REPEAT = TimeUnit.SECONDS.toMillis(5);

    private static Map<UserPlatform, UserProvider> providers = new HashMap<>();

    private @Getter Map<String, User> userCache = new ConcurrentHashMap<>();
    private @Getter File userDir = new File("koi/users/" + this + "/");

    static {
        providers.put(UserPlatform.CAFFEINE, new CaffeineUser.Provider());
        providers.put(UserPlatform.TWITCH, new TwitchUser.Provider());

        new RepeatingThread(REPEAT, () -> {
            JsonObject usernamesJson = new JsonObject(); // For internal tracking.
            long listeners = 0;
            long usercount = 0;

            for (UserPlatform platform : UserPlatform.values()) {
                Set<String> usernames = new HashSet<>();
                long current = System.currentTimeMillis();

                for (User user : new ArrayList<>(platform.userCache.values())) {
                    try {
                        if (user.hasListeners()) {
                            listeners += user.getEventListeners().size();
                            usercount++;
                            usernames.add(user.getUsername());
                            user.update();
                        } else {
                            long age = current - user.getLastWake();

                            if (Koi.getInstance().isDebug() || (age > REMOVE_AGE)) {
                                if (user.getUUID() != null) platform.userCache.remove(user.getUUID().toUpperCase());
                                if (user.getUsername() != null) platform.userCache.remove(user.getUsername().toUpperCase());

                                user.close();
                            }
                        }
                    } catch (Exception e) {
                        FastLogger.logStatic(LogLevel.SEVERE, "Ticking %s;%s produced an exception:", user.getUUID(), user.getPlatform());
                        FastLogger.logException(e);
                    }
                }

                JsonArray array = new JsonArray();

                usernames.forEach(array::add);

                usernamesJson.add(platform.name(), array);
            }

            JsonObject statsJson = new JsonObject();

            // All users are in the cache twice, under their username and uuid.
            statsJson.addProperty("listeners", listeners / 2);
            statsJson.addProperty("users", usercount / 2);

            FileUtil.writeJson(STATS, statsJson);
            FileUtil.writeJson(USERNAMES, usernamesJson);
        }).start();
    }

    private UserPlatform() {
        this.userDir.mkdirs();
    }

    public User getUser(String identifier) throws IdentifierException {
        return this.getUser(identifier, null);
    }

    @SneakyThrows
    public User getUser(String identifier, Object data) throws IdentifierException {
        try {
            User user = this.userCache.get(identifier.toUpperCase());

            if (user == null) {
                user = providers.get(this).get(identifier, data);

                if (user != null) {
                    if (user.getUUID() != null) this.userCache.put(user.getUUID().toUpperCase(), user);
                    if (user.getUsername() != null) this.userCache.put(user.getUsername().toUpperCase(), user);
                }
            } else {
                user.updateUser(data);
                user.wake();
            }

            return user;
        } catch (Exception e) {
            throw new IdentifierException();
        }
    }

    public static UserPlatform parse(JsonElement platformJson) throws PlatformException {
        if (platformJson == null) {
            return UserPlatform.CAFFEINE;
        } else if (platformJson.isJsonPrimitive()) {
            for (UserPlatform platform : UserPlatform.values()) {
                if (platform.name().equalsIgnoreCase(platformJson.getAsString())) {
                    return platform;
                }
            }
        }

        throw new PlatformException();
    }

    public static UserPlatform parse(JsonElement platformJson, String username) throws PlatformException {
        String[] split = username.split(";");

        if (split.length == 2) {
            for (UserPlatform platform : UserPlatform.values()) {
                if (platform.name().equalsIgnoreCase(split[1])) {
                    return platform;
                }
            }
        } else {
            return parse(platformJson);
        }

        return UserPlatform.CAFFEINE;
    }

}
