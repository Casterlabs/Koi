package co.casterlabs.koi;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import co.casterlabs.koi.networking.Server;
import co.casterlabs.koi.networking.SocketServer;
import co.casterlabs.koi.serialization.SerializedUserSerializer;
import co.casterlabs.koi.user.KoiAuthProvider;
import co.casterlabs.koi.user.User;
import co.casterlabs.koi.user.UserPlatform;
import lombok.Getter;
import lombok.SneakyThrows;
import xyz.e3ndr.consolidate.CommandRegistry;
import xyz.e3ndr.consolidate.exception.ArgumentsLengthException;
import xyz.e3ndr.consolidate.exception.CommandExecutionException;
import xyz.e3ndr.consolidate.exception.CommandNameException;
import xyz.e3ndr.fastloggingframework.logging.FastLogger;

public class Koi {
    //@formatter:off
    public static final Gson GSON = new GsonBuilder()
            .serializeNulls()
            .registerTypeAdapter(User.class, new SerializedUserSerializer())
            .create();
    //@formatter:on

    public static final String VERSION = "2.0.7";

    private static @Getter ThreadPoolExecutor eventThreadPool = new ThreadPoolExecutor(16, 128, 480, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static @Getter ThreadPoolExecutor clientThreadPool = new ThreadPoolExecutor(4, 16, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());
    private static @Getter ThreadPoolExecutor miscThreadPool = new ThreadPoolExecutor(2, 8, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private static @Getter Koi instance;

    // Koi things
    private Map<UserPlatform, KoiAuthProvider> authProviders = new ConcurrentHashMap<>();
    private CommandRegistry<Void> commandRegistry = new CommandRegistry<>();
    private @Getter Set<Server> servers = new HashSet<>();

    private @Getter FastLogger logger = new FastLogger();
    private @Getter KoiConfig config;

    static {
        eventThreadPool.setThreadFactory(new ThreadFactory() {
            private long threadNum = 0;

            @Override
            public Thread newThread(Runnable run) {
                Thread t = new Thread(run);

                t.setName("Event Thread Pool - Koi #" + this.threadNum++);

                return t;
            }
        });
        miscThreadPool.setThreadFactory(new ThreadFactory() {
            private long threadNum = 0;

            @Override
            public Thread newThread(Runnable run) {
                Thread t = new Thread(run);

                t.setName("Misc Thread Pool - Koi #" + this.threadNum++);

                return t;
            }
        });
    }

    public Koi(KoiConfig config) {
        if ((instance == null) || !instance.isRunning()) {
            instance = this;
        } else {
            throw new IllegalStateException("Koi is running, stop it in order to instantiate.");
        }

        this.config = config;

        this.servers.add(new SocketServer(new InetSocketAddress(this.config.getHost(), this.config.getPort()), this));

        this.commandRegistry.addCommand(new KoiCommands(this.commandRegistry));

        this.commandRegistry.addResolver((arg) -> {
            try {
                return UserPlatform.valueOf(arg.toUpperCase());
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }, UserPlatform.class);

        (new Thread() {
            @SuppressWarnings("resource")
            @Override
            public void run() {
                Scanner in = new Scanner(System.in);

                while (true) {
                    try {
                        commandRegistry.execute(in.nextLine());
                    } catch (CommandNameException | CommandExecutionException | ArgumentsLengthException e) {
                        logger.exception(e);
                    }
                }
            }
        }).start();
    }

    public void addAuthProvider(KoiAuthProvider provider) {
        this.authProviders.put(provider.getPlatform(), provider);
    }

    public boolean isRunning() {
        for (Server server : this.servers) {
            if (!server.isRunning()) {
                return false;
            }
        }

        return true;
    }

    public KoiAuthProvider getAuthProvider(UserPlatform platform) {
        return this.authProviders.get(platform);
    }

    @SneakyThrows
    public void start() {
        if (!this.isRunning()) {
            this.servers.forEach(Server::start);
        }
    }

    public void stop() {
        if (this.isRunning()) {
            this.servers.forEach(Server::stop);

            this.logger.info("Stopped koi!");
        }
    }

}
