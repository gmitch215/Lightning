package me.gamercoder215.lightning;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Version;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import javax.annotation.Nonnull;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;

public class Lightning extends JavaPlugin implements Listener {

    protected static Map<Class<?>, Function<String, ?>> registeredParse = new HashMap<>();
    protected static List<URI> eventListeners;

    private static HttpServer server;
    private static final Gson gson = new Gson();

    protected static final HttpClient client = HttpClient.newBuilder().version(Version.HTTP_2).build();

    public void onEvent(Event e) {
        try {
            for (URI uri : eventListeners) {
            	if (uri == null) continue;
            	
                HttpRequest req = HttpRequest.newBuilder(uri)
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(new EventInfo(e))))
                .setHeader("User-Agent", "Java 17 Lightning HttpClient")
                .setHeader("Content-Type", "application/json")
                .build();

                client.send(req, HttpResponse.BodyHandlers.ofString());
            }
        } catch (Exception err) {
            err.printStackTrace();
        }
    }

    private static Object serializeObject(Object obj) {
        if (obj instanceof Enum<?> enumerical) {
            return enumerical.name();
        } else if (obj instanceof ConfigurationSerializable serialize) {
            return serialize.serialize();
        } else if (obj instanceof String s) return s;
        else return obj.toString();
    }

    protected static final class EventInfo {

        public final String name;
        public final String full_name;
        public final Map<String, Object> event_data;

        private EventInfo(Event e) {
            this.name = e.getClass().getSimpleName();
            this.full_name = e.getClass().getName();

            Map<String, Object> data = new HashMap<>();
            
            for (Field f : e.getClass().getDeclaredFields()) {
                f.setAccessible(true);
                Map<String, Object> fieldData = new HashMap<>();

                fieldData.put("type", f.getType().getName());
                try {
                    fieldData.put("value", serializeObject(f.get(e)));
                } catch (Exception err) {
                    err.printStackTrace();
                }

                data.put(f.getName(), fieldData);
            }

            this.event_data = data;
        }
    }

    /**
     * Fetch all of the registered parameters.
     * @return Map of registered parameters to their functions
     */
    public static Map<Class<?>, Function<String, ?>> getRegistered() {
        return registeredParse;
    }

    /**
     * Removes an Event Listener from the registered URIs.
     * @param uri URI to remove
     */
    public static void removeEventListener(@Nonnull URI uri) {
        eventListeners.remove(uri);
    }

    /**
     * Removes a Event Listener from the registered URIs.
     * @param url URL to remove
     * @throws IllegalArgumentException if URL is invalid
     * @see #removeEventListener(URI)
     */
    public static void removeEventListener(@Nonnull String url) throws IllegalArgumentException {
        removeEventListener(URI.create(url));
    }

    /**
     * Get the list of all registered event listeners.
     * @return List of event listeners 
     */
    public static List<URI> getEventListeners() {
        return eventListeners;
    }

    /**
     * Adds this URL as an event listener.
     * @param url URL to add
     * @throws IllegalArgumentException if url is invalid
     * @see {@link #addEventListener(URI)}
     */
    public static void addEventListener(@Nonnull String url) throws IllegalArgumentException {
        addEventListener(URI.create(url));
    }

    /**
     * Adds this URI as an event lsitener.
     * <p>
     * When an event happens, a POST request will be sent to this URI with the event information. 
     * @param uri URI to add
     */
    public static void addEventListener(@Nonnull URI uri) {
        eventListeners.add(uri);
    }

    /**
     * Register a parameter that can be parsed in HTTP.
     * <p>
     * Most base methods (int, double, List, Map) and typical Bukkit Methods (Location, Player, World) are pre-registered. You can check the wiki if you need to parse a parameter to call a method.
     * @param <T> Type of Object to parse
     * @param clazz Class of Paramatized Object
     * @param parse Function to parse from a string in HTTP
     */
    public static <T extends Object> void registerParameter(Class<T> clazz, Function<String, T> parse) {
        registeredParse.put(clazz, parse);
    }

    /**
     * Attempts to parse this class from a string input.
     * <p>
     * This will lookup classes from {@link #getRegistered()} and perform their functions there. If your class is not inside, this will throw a {@link NullPointerException}.
     * @param <T> Type of class to parse
     * @param clazz Class to parse
     * @param input Input to parse
     * @return Parsed Class
     * @throws NullPointerException if parsed does not contain class
     */
    @SuppressWarnings("unchecked")
    public static <T> T parse(Class<T> clazz, String input) throws NullPointerException {
        return (T) registeredParse.get(clazz).apply(input);
    }

    public static boolean isOnline() {
        Lightning plugin = JavaPlugin.getPlugin(Lightning.class);
        int port = plugin.getConfig().isString("port") ? Bukkit.getPort() : plugin.getConfig().getInt("port");

        try {
            Socket socket = new Socket();
            socket.connect(new InetSocketAddress("localhost", port), 10);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void setupCommands() {
        getLogger().info("Loading Commands...");
        
        new Commands(this);
    }

    private void registerBaseMethods() {
        getLogger().info("Registering Parameters...");

        registerParameter(int.class, s -> { return Integer.parseInt(s); });
        registerParameter(boolean.class, s -> { return Boolean.parseBoolean(s); });
        registerParameter(float.class, s -> { return Float.parseFloat(s); });
        registerParameter(long.class, s -> { return Long.parseLong(s); });
        registerParameter(double.class, s -> { return Double.parseDouble(s); });
        registerParameter(byte.class, s -> { return Byte.parseByte(s); });
        registerParameter(short.class, s -> { return Short.parseShort(s); });
        registerParameter(char.class, s -> { return s.charAt(0); });

        registerParameter(boolean[].class, s -> {
            boolean[] arr = new boolean[s.split(" ").length];

            for (int i = 0; i < s.split(" ").length; i++) {
                arr[i] = Boolean.parseBoolean(s.split(" ")[i]);
            }

            return arr;
        });
        registerParameter(int[].class, s -> {
            int[] arr = new int[s.split(" ").length];

            for (int i = 0; i < s.split(" ").length; i++) {
                arr[i] = Integer.parseInt(s.split(" ")[i]);
            }

            return arr;
        });
        registerParameter(byte[].class, s -> {
            byte[] arr = new byte[s.split(" ").length];

            for (int i = 0; i < s.split(" ").length; i++) {
                arr[i] = Byte.parseByte(s.split(" ")[i]);
            }

            return arr;
        });
        registerParameter(short[].class, s -> {
            short[] arr = new short[s.split(" ").length];

            for (int i = 0; i < s.split(" ").length; i++) {
                arr[i] = Short.parseShort(s.split(" ")[i]);
            }

            return arr;
        });
        registerParameter(char[].class, s -> { return s.toCharArray(); });
        registerParameter(double[].class, s -> {
            double[] arr = new double[s.split(" ").length];

            for (int i = 0; i < s.split(" ").length; i++) {
                arr[i] = Double.parseDouble(s.split(" ")[i]);
            }

            return arr;
        });
        registerParameter(float[].class, s -> {
            float[] arr = new float[s.split(" ").length];

            for (int i = 0; i < s.split(" ").length; i++) {
                arr[i] = Float.parseFloat(s.split(" ")[i]);
            }

            return arr;
        });
        

        registerParameter(String.class, s -> { return s; });
        registerParameter(String[].class, s -> { return s.split(" "); });
        registerParameter(Class.class, s -> { 
            try {
                return Class.forName(s);
            } catch (Exception e) {
                return null;
            }
        });

        registerParameter(UUID.class, s -> { return UUID.fromString(s); });
        registerParameter(List.class, s -> { return Arrays.asList(s.split(" ")); });
        registerParameter(Map.class, s -> {
            String ds = s.replace("(", "").replace(")", "");
            Map<String, String> data = new HashMap<>();

            for (String entry : ds.split(",")) {
                data.put(entry.split("-")[0], entry.split("-")[1]);
            }

            return data;
        });
        registerParameter(Enum.class, s -> {
            try {
                @SuppressWarnings("rawtypes")
                Class<? extends Enum> clazz = Class.forName(s.split(":")[0]).asSubclass(Enum.class);
                String enumerial = s.split(":")[1].toUpperCase();
            
                if (!(clazz.isEnum())) return null;
                
                @SuppressWarnings("unchecked")
                Enum<?> enumValue = Enum.valueOf(clazz, enumerial);
                
                return enumValue;
            } catch (Exception e) {
                return null;
            }
        });


        registerParameter(OfflinePlayer.class, s -> { return Bukkit.getOfflinePlayer(UUID.fromString(s)); });
        registerParameter(Player.class, s -> {
            try {
                return Bukkit.getPlayer(UUID.fromString(s));
            } catch (IllegalArgumentException e) {
                return Bukkit.getPlayer(s);
            }
        });
        registerParameter(Entity.class, s -> { return Bukkit.getEntity(UUID.fromString(s)); });
        registerParameter(LivingEntity.class, s -> {
            if (!(Bukkit.getEntity(UUID.fromString(s)) instanceof LivingEntity en)) {
                return null;
            }

            return en;
        });
        registerParameter(ItemStack.class, s -> {
            String ds = s.replace("(", "").replace(")", "");
            Map<String, String> data = new HashMap<>();

            for (String entry : ds.split(",")) {
                data.put(entry.split("-")[0], entry.split("-")[1]);
            }

            return new ItemStack(Material.getMaterial(data.get("item")), data.containsKey("amount") ? Integer.parseInt(data.get("amount")) : 1);
        });

        registerParameter(World.class, s -> { return Bukkit.getWorld(s); });
        registerParameter(CommandSender.class, s -> { return Bukkit.getPlayer(s); });
        registerParameter(Location.class, s -> {
            String ds = s.replace("(", "").replace(")", "");
            Map<String, String> data = new HashMap<>();

            for (String entry : ds.split(",")) {
                data.put(entry.split("\\*")[0], entry.split("\\*")[1]);
            }
            
            World w = Bukkit.getWorld(data.get("world"));
            int x = Integer.parseInt("x");
            int y = Integer.parseInt("y");
            int z = Integer.parseInt("z");

            if (!(data.containsKey("yaw"))) {
                return new Location(w, x, y, z);
            } else {
                float yaw = Float.parseFloat("yaw");
                float pitch = Float.parseFloat("pitch");

                return new Location(w, x, y, z, yaw, pitch);
            }
        });
        registerParameter(NamespacedKey.class, s -> {
            String ds = s.replace("(", "").replace(")", "");
            Map<String, String> data = new HashMap<>();

            for (String entry : ds.split(",")) {
                data.put(entry.split("\\*")[0], entry.split("\\*")[1]);
            }

            String namespace = data.get("namespace");
            String key = data.get("key");

            if (namespace.equals("minecraft")) {
                return NamespacedKey.minecraft(key);
            } else {
                return new NamespacedKey(Bukkit.getPluginManager().getPlugin(namespace), key);
            }
        }); 
        registerParameter(Plugin.class, s -> { return Bukkit.getPluginManager().getPlugin(s); });
        registerParameter(Block.class, s -> {
            String ds = s.replace("(", "").replace(")", "");
            Map<String, String> data = new HashMap<>();

            for (String entry : ds.split(",")) {
                data.put(entry.split("-")[0], entry.split("-")[1]);
            }
            
            World w = Bukkit.getWorld(data.get("world"));
            int x = Integer.parseInt("x");
            int y = Integer.parseInt("y");
            int z = Integer.parseInt("z");

            return new Location(w, x, y, z).getBlock();
        });

        getLogger().info("Registered Base Methods");
    }

    private void startServer() {
    	if (Lightning.isOnline()) {
    		getLogger().severe("Port in use, disabling");
    		Bukkit.getPluginManager().disablePlugin(this);
    		return;
    	}
    	
        try {
            int port = getConfig().getInt("port");
            
            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            
            server.createContext("/", new HTTPHandler(this));

            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the HTTP Server that is hosting the server.
     * @return HttpServer object
     */
    public static HttpServer getWebServer() {
        return server;
    }

    private void loadConfiguration() {
        getLogger().info("Loading Configuration...");
        FileConfiguration config = getConfig();

        if (!(config.isSet("port"))) {
            config.set("port", "SERVER");
        }

        if (!(config.isList("listeners"))) {
            config.set("listeners", new ArrayList<String>());
        }


        if (!(config.isConfigurationSection("headers"))) {
            config.createSection("headers");
        }

        ConfigurationSection headers = config.getConfigurationSection("headers");

        if (!(headers.isString("Authorization"))) {
            headers.set("Authorization", "NONE");
        }

        saveConfig();

        List<URI> eventListeners = new ArrayList<>();

        for (String s : config.getStringList("listeners")) eventListeners.add(URI.create(s));

        Lightning.eventListeners = eventListeners;
    }

    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();
        setupCommands();
        registerBaseMethods();
        startServer();

        getLogger().info("Loading listener...");

        RegisteredListener registeredListener = new RegisteredListener(this, (listener, event) -> onEvent(event), EventPriority.NORMAL, this, false);
        for (HandlerList handler : HandlerList.getHandlerLists())
            handler.register(registeredListener);

        getLogger().info("Done!");
    }

    public void onDisable() {
        FileConfiguration config = getConfig();
        
        List<String> listeners = new ArrayList<>();

        for (URI uri : eventListeners) {
            listeners.add(uri.toString());
        }

        config.set("listeners", listeners);
        
        getLogger().info("Stopping server...");
        
        if (server != null) server.stop(0);

        getLogger().info("Finished Disabling Lightning");
    }

}