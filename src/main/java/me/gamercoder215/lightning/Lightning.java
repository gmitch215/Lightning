package me.gamercoder215.lightning;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
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
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.SpawnCategory;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.plugin.java.JavaPlugin;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletHandler;

import com.google.gson.Gson;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import me.gamercoder215.lightning.ClassInfo.FieldInfo;
import me.gamercoder215.lightning.ClassInfo.MethodInfo;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;

public class Lightning extends JavaPlugin implements Listener {

    protected static Map<Class<?>, Function<String, ?>> registeredParse = new HashMap<>();
    protected static List<URI> eventListeners;

    private static Server server;
    private static final Gson gson = new Gson();

    protected static final HttpClient client = HttpClient.newBuilder().version(Version.HTTP_2).build();
    
    public final static class HTTPHandler extends HttpServlet {
		private static final long serialVersionUID = 7291695377779784977L;
		private static Gson gson = new Gson();
		
		public HTTPHandler() {};
		
        private static record ErrRes(int code, String message) {};

        private static String RES_404 = gson.toJson(new ErrRes(404, "The requested object cannot be resolved."));
        private static String RES_403 = gson.toJson(new ErrRes(403, "Unauthorized"));

        private static String RES_400(String msg) {
            return gson.toJson(new ErrRes(400, msg));
        }

        private static record ExceptionRes(int code, String exType, String message) {};

        private static record OtherRes(int code, Object response) {};
        
        private static List<Method> getAllMethods(Class<?> clazz) {
        	List<Method> methods = new ArrayList<>();
        	
        	methods.addAll(Arrays.asList(clazz.getDeclaredMethods()));
        	if (clazz.getSuperclass() != null) {
        		methods.addAll(getAllMethods(clazz.getSuperclass()));
        	}
        	
        	return methods;
        }
        
        @Override
        public void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
            res.setContentType("application/json");
            
            Map<String, String> params = queryToMap(req.getQueryString());
            Map<String, String> headers = new HashMap<>();
            
            Lightning plugin = JavaPlugin.getPlugin(Lightning.class);
            
            for (Map.Entry<String, Object> entry : plugin.getConfig().getConfigurationSection("headers").getValues(false).entrySet()) {
                if (!(entry.getValue() instanceof String value)) continue;
                if (value.equalsIgnoreCase("none")) continue;
                
                headers.put(entry.getKey(), value);
            }
            
            boolean authorized = true;

            for (String key : headers.keySet()) {
            	if (req.getHeader(key) == null || !(req.getHeader(key).equals(headers.get(key)))) {
            		authorized = false;
            		break;
            	}
            }
            
            boolean hasKey = !(plugin.getConfig().getString("key").equals("NONE"));
            
            if (hasKey) {
            	if (!(params.containsKey("key")) || !params.get("key").equals(plugin.getConfig().getString("key"))) {
            		authorized = false;
            	}
            }
            

            if (!authorized) {
            	res.setStatus(403);
            	res.setContentLength(RES_403.length());
                res.getWriter().println(RES_403);
                return;
            }
            
            try {
                String path = req.getRequestURI();

                if (path.equals("/")) {
                	res.setStatus(200);
                	res.setContentLength("200 OK".length());
                	res.getWriter().println("200 OK");
                	return;
                } else if (path.startsWith("/addlistener/")) {
                    String url = path.split("/addlistener/")[1];

                    try {
                        Lightning.addEventListener(url);
                        String response = "{\"code\":200, \"message\": \"Successfully added listener\"}";

                        res.setStatus(200);
                        res.setContentLength(response.length());
                        res.getWriter().println(response);
                    } catch (IllegalArgumentException e) {
                        res.setStatus(400);
                        res.setContentLength(RES_400("Invalid URL").length());
                        res.getWriter().println(RES_400("Invalid URL"));
                    }
                    return;
                } else if (path.startsWith("/removelistener/")) {
                    String url = path.split("/removelistener/")[1];

                    try {
                        if (!(url.startsWith("https://")) && !(url.startsWith("http://"))) url = "https://" + url;

                        Lightning.removeEventListener(url);
                        String response = "{\"code\":200, \"message\": \"Successfully removed listener\"}";

                        res.setStatus(200);
                        res.setContentLength(response.length());
                        res.getWriter().println(response);
                    } catch (IllegalArgumentException e) {
                    	res.setStatus(400);
                    	res.setContentLength(RES_400("Invalid URL").length());
                        res.getWriter().println(RES_400("Invalid URL"));
                    }
                    return;
                } else if (path.startsWith("/class/")) {         
                    String endpoint = path.split("/class/")[1];   
                    String clazzName = endpoint.replace('/', '.');

                    Class<?> clazz = Class.forName(clazzName);

                    if (params.containsKey("field")) {
                        try {
                            Field f = clazz.getDeclaredField(params.get("field"));
                            
                            String response = gson.toJson(new FieldInfo(f));
                            res.setStatus(200);
                            res.setContentLength(response.length());
                            res.getWriter().println(response);
                        } catch (NoSuchFieldException e) {
                            res.setStatus(404);
                            res.setContentLength(RES_404.length());
                            res.getWriter().println(RES_404);
                        }
                        return;
                    } else if (params.containsKey("method")) {
                        try {
                            Method m = clazz.getDeclaredMethod(params.get("method"));
                            
                            String response = gson.toJson(new MethodInfo(m));
                            res.setStatus(200);
                            res.setContentLength(response.length());
                            res.getWriter().println(response);
                        } catch (NoSuchMethodException e) {
                            res.setStatus(404);
                            res.setContentLength(RES_404.length());
                            res.getWriter().println(RES_404);
                        }
                        return;
                    } else {
                        String response = gson.toJson(new ClassInfo(clazz));
                        res.setStatus(200);
                        res.setContentLength(response.length());
                        res.getWriter().println(response);
                    }
                } else if (path.startsWith("/method/")) {
                    String endpoint = path.split("/method/")[1];
                    String clazzName = endpoint.replace('/', '.');

                    Class<?> clazz = Class.forName(clazzName);

                    if (!(params.containsKey("method"))) {
                        res.setStatus(400);
                        res.setContentLength(RES_400("Missing parameter method").length());
                        res.getWriter().println(RES_400("Missing parameter method"));
                        return;
                    }

                    String method = params.get("method");

                    try {
                        List<Method> possible = new ArrayList<>();
                        
                        for (Method m : getAllMethods(clazz)) if (m.getName().equals(method)) possible.add(m);
                        if (possible.size() < 1) throw new NoSuchMethodException();

                        Method chosen = null;
                        List<Object> args = new ArrayList<>();
                        
                        for (Method m : possible) {
                            if (m.getParameterCount() != params.keySet().stream().filter(s -> s.startsWith("arg")).toList().size()) continue;

                            if (m.getParameterCount() > 0) {
                                boolean continueLoop = true;

                                for (int i = 0; i < m.getParameterCount(); i++) {
                                    Parameter p = m.getParameters()[i];
                                    if (!(Lightning.getRegistered().containsKey(p.getType()))) continue;

                                    try {
                                        Object obj = Lightning.parse(p.getType(), params.get("arg" + i));
                                        if (obj == null) throw new NullPointerException();
                                        
                                        args.add(obj);
                                    } catch (IllegalArgumentException | NullPointerException e) {
                                        continue;
                                    }
                                    continueLoop = false;
                                }

                                if (continueLoop) continue;
                                else {
                                    chosen = m;
                                    break;
                                }
                            } else {
                                chosen = m;
                                break;
                            }
                        }

                        if (chosen == null) throw new NoSuchMethodException();

                        Object inst = null;

                        if (!(params.containsKey("instance")) && !(Modifier.isStatic(chosen.getModifiers()))) {
                        	res.setStatus(400);
                            res.setContentLength(RES_400("Missing parameter instance").length());
                            res.getWriter().println(RES_400("Missing parameter instance"));
                            return;
                        }

                        if (params.containsKey("instance")) {
                            inst = Lightning.parse(chosen.getDeclaringClass(), params.get("instance"));
                        }

                        if (inst == null && !(Modifier.isStatic(chosen.getModifiers()))) {
                        	res.setStatus(400);
                            res.setContentLength(RES_400("Malformed parameter instance").length());
                            res.getWriter().println(RES_400("Malformed parameter instance"));
                            return;
                        }

                        try {
                            chosen.setAccessible(true);
                            Object responseO;
                            if (Modifier.isStatic(chosen.getModifiers())) {
                                if (args.size() == 0) {
                                    responseO = chosen.invoke(null);
                                } else {
                                    responseO = chosen.invoke(null, args.toArray());
                                }
                            } else {
                                if (args.size() == 0) {
                                    responseO = chosen.invoke(inst);
                                } else {
                                    responseO = chosen.invoke(inst, args.toArray());
                                }
                            }
                            String response = gson.toJson(new OtherRes(200, responseO));
                            res.setStatus(200);
                            res.setContentLength(response.length());
                            res.getWriter().println(response);
                        } catch (InvocationTargetException e) {
                            String response = gson.toJson(new ExceptionRes(400, e.getCause().getClass().getName(), e.getCause().getMessage()));
                        	res.setStatus(400);
                            res.setContentLength(response.length());
                            res.getWriter().println(response);
                    	} catch (Exception e) {
                            String response = gson.toJson(new ExceptionRes(400, e.getClass().getName(), e.getMessage()));
                        	res.setStatus(400);
                            res.setContentLength(response.length());
                            res.getWriter().println(response);
                            return;
                            
                        }
                    } catch (NoSuchMethodException e) {
                        String response = RES_400("No method " + method);
                    	res.setStatus(400);
                        res.setContentLength(response.length());
                        res.getWriter().println(response);
                        return;   
                    }
                } else {
                	res.setStatus(404);
                    res.setContentLength(RES_404.length());
                    res.getWriter().println(RES_404);
                }
            } catch (Exception e) {
            	e.printStackTrace();
            	res.setStatus(404);
                res.setContentLength(RES_404.length());
                res.getWriter().println(RES_404);
            }
        }
        
        private static Map<String, String> queryToMap(String query) {
            if(query == null) {
                return new HashMap<>();
            }
            Map<String, String> result = new HashMap<>();

            if (query.contains("&")) {
                for (String param : query.split("&")) {
                    String[] entry = param.split("=");
                    if (entry.length > 1) {
                        result.put(entry[0], entry[1]);
                    } else {
                        result.put(entry[0], "");
                    }
                }
            } else {
                String[] entry = query.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], entry[1]);
                } else {
                    result.put(entry[0], "");
                }
            } 
            return result;
        }    
    }
    
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
     * @see #addEventListener(URI)
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
        registerParameter(char[].class, s -> { return s.replace("%2B", "").toCharArray(); });
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
        

        registerParameter(String.class, s -> {
        	String newS = s.replace("%26", "&").replace("%20", " ");
        	return ChatColor.translateAlternateColorCodes('&', newS);
        });
        registerParameter(String[].class, s -> { return s.split("%2B"); });
        registerParameter(Class.class, s -> { 
            try {
                return Class.forName(s);
            } catch (Exception e) {
                return null;
            }
        });

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
        registerParameter(Material.class, s -> { return Material.getMaterial(s); });
        registerParameter(Particle.class, s -> { try { return Particle.valueOf(s); } catch (IllegalArgumentException e) { return null; } });
        registerParameter(SpawnCategory.class, s -> { try { return SpawnCategory.valueOf(s); } catch (IllegalArgumentException e) { return null; } });
        
        registerParameter(BaseComponent.class, s -> {
        	String newS = s.replace("%26", "&");
        	return new TextComponent(ChatColor.translateAlternateColorCodes('&', newS));
        });
        registerParameter(TextComponent.class, s -> {
        	String newS = s.replace("%26", "&");
        	return new TextComponent(ChatColor.translateAlternateColorCodes('&', newS));
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
            
            server = new Server();
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            server.addConnector(connector);
            
            ServletHandler handler = new ServletHandler();
            handler.addServletWithMapping(HTTPHandler.class, "/");
            server.setHandler(handler);

            server.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Get the HTTP Server that is hosting the server.
     * @return Jetty Server object
     */
    public static Server getWebServer() {
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
        
        if (!(config.isString("key"))) {
        	config.set("key", "NONE");
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
        
        if (server != null)
			try {
				server.stop();
			} catch (Exception e) {
				e.printStackTrace();
			}

        getLogger().info("Finished Disabling Lightning");
    }

}