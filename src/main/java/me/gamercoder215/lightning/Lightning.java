package me.gamercoder215.lightning;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import com.sun.net.httpserver.HttpServer;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import revxrsal.commands.bukkit.BukkitCommandHandler;

public class Lightning extends JavaPlugin {

    protected static Map<Class<?>, Function<String, ?>> registeredParse = new HashMap<>();

    private static HttpServer server;
    private static BukkitCommandHandler handler;

    /**
     * Fetch all of the registered parameters.
     * @return Map of registered parameters to their functions
     */
    public static Map<Class<?>, Function<String, ?>> getRegistered() {
        return registeredParse;
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

    private void setupLamp() {
        getLogger().info("Loading Commands...");
        handler = BukkitCommandHandler.create(this);

        

        handler.registerBrigadier();
    }

    private static void registerBaseMethods() {
        // getLogger().info("Registering Parameters...");

        registerParameter(int.class, s -> { return Integer.parseInt(s); });
        registerParameter(boolean.class, s -> { return Boolean.parseBoolean(s); });
        registerParameter(float.class, s -> { return Float.parseFloat(s); });
        registerParameter(long.class, s -> { return Long.parseLong(s); });
        registerParameter(double.class, s -> { return Double.parseDouble(s); });
        registerParameter(byte.class, s -> { return Byte.parseByte(s); });
        registerParameter(short.class, s -> { return Short.parseShort(s); });
        registerParameter(char.class, s -> { return s.charAt(0); });

        registerParameter(boolean[].class, s -> {
            boolean[] arr = new boolean[s.split("%20").length];

            for (int i = 0; i < s.split("%20").length; i++) {
                arr[i] = Boolean.parseBoolean(s.split("%20")[i]);
            }

            return arr;
        });
        registerParameter(int[].class, s -> {
            int[] arr = new int[s.split("%20").length];

            for (int i = 0; i < s.split("%20").length; i++) {
                arr[i] = Integer.parseInt(s.split("%20")[i]);
            }

            return arr;
        });
        registerParameter(byte[].class, s -> {
            byte[] arr = new byte[s.split("%20").length];

            for (int i = 0; i < s.split("%20").length; i++) {
                arr[i] = Byte.parseByte(s.split("%20")[i]);
            }

            return arr;
        });
        registerParameter(short[].class, s -> {
            short[] arr = new short[s.split("%20").length];

            for (int i = 0; i < s.split("%20").length; i++) {
                arr[i] = Short.parseShort(s.split("%20")[i]);
            }

            return arr;
        });
        registerParameter(char[].class, s -> {
            char[] arr = new char[s.split("%20").length];

            for (int i = 0; i < s.split("%20").length; i++) {
                arr[i] = s.split("%20")[i].charAt(0);
            }

            return arr;
        });
        registerParameter(double[].class, s -> {
            double[] arr = new double[s.split("%20").length];

            for (int i = 0; i < s.split("%20").length; i++) {
                arr[i] = Double.parseDouble(s.split("%20")[i]);
            }

            return arr;
        });
        registerParameter(float[].class, s -> {
            float[] arr = new float[s.split("%20").length];

            for (int i = 0; i < s.split("%20").length; i++) {
                arr[i] = Float.parseFloat(s.split("%20")[i]);
            }

            return arr;
        });
        

        registerParameter(String.class, s -> { return s; });
        registerParameter(UUID.class, s -> { return UUID.fromString(s); });
        registerParameter(List.class, s -> { return Arrays.asList(s.split("%20")); });
        registerParameter(Map.class, s -> {
            String ds = s.replace("(", "").replace(")", "");
            Map<String, String> data = new HashMap<>();

            for (String entry : ds.split(",")) {
                data.put(entry.split("-")[0], entry.split("-")[1]);
            }

            return data;
        });

        registerParameter(OfflinePlayer.class, s -> { return Bukkit.getOfflinePlayer(UUID.fromString(s)); });
        registerParameter(Player.class, s -> { return Bukkit.getPlayer(s); });
        registerParameter(World.class, s -> { return Bukkit.getWorld(s); });
        registerParameter(Material.class, s -> { return Material.matchMaterial(s.toUpperCase()); });
        registerParameter(CommandSender.class, s -> { return Bukkit.getPlayer(s); });
        registerParameter(SpawnCategory.class, s -> { return SpawnCategory.valueOf(s.toUpperCase()); });
        registerParameter(Location.class, s -> {
            String ds = s.replace("(", "").replace(")", "");
            Map<String, String> data = new HashMap<>();

            for (String entry : ds.split(",")) {
                data.put(entry.split("-")[0], entry.split("-")[1]);
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
                data.put(entry.split("-")[0], entry.split("-")[1]);
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

        // getLogger().info("Registered Base Methods");
    }

    private void startServer() {
        try {
            int port = getConfig().isString("port") ? Bukkit.getPort() : getConfig().getInt("port");

            server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
            server.createContext("/", new HTTPHandler());   

            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the HTTP Server that is hosting the server.
     * @return HttpServer object
     */
    public static HttpServer getHTTPServer() {
        return server;
    }

    private void loadConfiguration() {
        getLogger().info("Loading Configuration...");
        FileConfiguration config = getConfig();

        if (!(config.isSet("port"))) {
            config.set("port", "SERVER");
        }

        saveConfig();
    }

    public void onEnable() {
        saveDefaultConfig();
        loadConfiguration();
        setupLamp();
        registerBaseMethods();
        startServer();

        getLogger().info("Done!");
    }

}