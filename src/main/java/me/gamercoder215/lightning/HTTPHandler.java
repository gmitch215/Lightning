package me.gamercoder215.lightning;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import org.bukkit.ChatColor;

import me.gamercoder215.lightning.ClassInfo.FieldInfo;
import me.gamercoder215.lightning.ClassInfo.MethodInfo;

class HTTPHandler implements HttpHandler {

    private final Lightning plugin;

    public HTTPHandler(Lightning plugin) {
        this.plugin = plugin;
    }

    private static Gson gson = new Gson();

    private static record ErrRes(int code, String message) {};

    private static String RES_404 = gson.toJson(new ErrRes(404, "The requested object cannot be resolved."));
    private static String RES_403 = gson.toJson(new ErrRes(403, "Unauthorized"));

    private static String RES_400(String msg) {
        return gson.toJson(new ErrRes(400, msg));
    }

    private static record ExceptionRes(int code, String exType, String message) {};

    private static record OtherRes(int code, Object response) {};

    @Override
    public void handle(HttpExchange ex) throws IOException {
        OutputStream os = ex.getResponseBody();
        ex.getResponseHeaders().set("Content-Type", "application/json");

        Map<String, String> headers = new HashMap<>();

        for (Map.Entry<String, Object> entry : plugin.getConfig().getConfigurationSection("headers").getValues(false).entrySet()) {
            if (!(entry.getValue() instanceof String value)) continue;
            if (value.equalsIgnoreCase("none")) continue;
            
            headers.put(entry.getKey(), value);
        }

        boolean authorized = true;

        for (String key : ex.getRequestHeaders().keySet()) {
            if (headers.get(key) == null || !(headers.get(key).equals(ex.getRequestHeaders().getFirst(key)))) {
                authorized = false;
                break;
            }
        }

        if (!authorized) {
            ex.sendResponseHeaders(403, RES_403.length());
            os.write(RES_403.length());
            os.close();
        }
        
        try {
            Map<String, String> params = queryToMap(URLEncoder.encode(ex.getRequestURI().getQuery(), Charset.forName("UTF-8")));
            String path = URLDecoder.decode(ex.getRequestURI().getPath(), Charset.forName("UTF-8"));

            if (path.startsWith("/addlistener/")) {
                String url = path.split("/addlistener/")[1];

                try {
                    Lightning.addEventListener(URLDecoder.decode(url, Charset.forName("UTF-8")));
                    String res = "{\"code\":200, \"message\": \"Successfully added listener\"}";

                    ex.sendResponseHeaders(200, res.length());
                    os.write(res.getBytes());
                } catch (IllegalArgumentException e) {
                    ex.sendResponseHeaders(400, RES_400("Invalid URL").length());
                    os.write(RES_400("Invalid URL").length());
                }
                os.close();
                return;
            } else if (path.startsWith("/removelistener/")) {
                String url = path.split("/removelistener/")[1];

                try {
                    if (!(url.startsWith("https://")) && !(url.startsWith("http://"))) url = "https://" + url;

                    Lightning.removeEventListener(URLDecoder.decode(url, Charset.forName("UTF-8")));
                    String res = "{\"code\":200, \"message\": \"Successfully removed listener\"}";

                    ex.sendResponseHeaders(200, res.length());
                    os.write(res.getBytes());
                } catch (IllegalArgumentException e) {
                    ex.sendResponseHeaders(400, RES_400("Invalid URL").length());
                    os.write(RES_400("Invalid URL").length());
                }
                os.close();
                return;
            } else if (path.startsWith("/class/")) {         
                String endpoint = path.split("/class/")[1];   
                String clazzName = endpoint.replace('/', '.');

                Class<?> clazz = Class.forName(clazzName);

                if (params.containsKey("field")) {
                    try {
                        Field f = clazz.getDeclaredField(params.get("field"));
                        
                        String res = gson.toJson(new FieldInfo(f));
                        ex.sendResponseHeaders(200, res.length());
                        os.write(res.getBytes());
                    } catch (NoSuchFieldException e) {
                        ex.sendResponseHeaders(404, RES_404.length());
                        os.write(RES_404.getBytes());
                        os.close();
                    }
                } else if (params.containsKey("method")) {
                    try {
                        Method m = clazz.getDeclaredMethod(params.get("method"));
                        
                        String res = gson.toJson(new MethodInfo(m));
                        ex.sendResponseHeaders(200, res.length());
                        os.write(res.getBytes());
                    } catch (NoSuchMethodException e) {
                        ex.sendResponseHeaders(404, RES_404.length());
                        os.write(RES_404.getBytes());
                        os.close();
                    }
                } else {
                    String res = gson.toJson(new ClassInfo(clazz));
                    ex.sendResponseHeaders(200, res.length());
                    os.write(res.getBytes());
                }

                os.close();
            } else if (path.startsWith("/method/")) {
                String endpoint = path.split("/method/")[1];
                String clazzName = endpoint.replace('/', '.');

                Class<?> clazz = Class.forName(clazzName);

                if (!(params.containsKey("method"))) {
                    ex.sendResponseHeaders(400, RES_400("Missing parameter method").length());
                    os.write(RES_400("Missing parameter method").getBytes());
                    os.close();
                    return;
                }

                String method = params.get("method");

                try {
                    List<Method> possible = new ArrayList<>();

                    for (Method m : clazz.getDeclaredMethods()) if (m.getName().equals(method)) possible.add(m);
                    if (possible.size() < 1) throw new NoSuchMethodException();

                    Method chosen = null;
                    List<Object> args = new ArrayList<>();
                    boolean format = (params.containsKey("format") ? Boolean.parseBoolean(params.get("format")) : false);

                    for (Method m : possible) {
                        if (m.getParameterCount() != params.size() - 1) continue;

                        if (m.getParameterCount() > 0) {
                            boolean continueLoop = true;

                            for (int i = 0; i < m.getParameterCount(); i++) {
                                Parameter p = m.getParameters()[i];
                                if (!(Lightning.getRegistered().containsKey(p.getType()))) continue;

                                try {
                                    Object obj = Lightning.parse(p.getType(), params.get("arg" + i));
                                    if (obj == null) throw new NullPointerException();

                                    if (obj instanceof String s && format) {
                                        args.add(ChatColor.translateAlternateColorCodes('&', s));
                                    } else args.add(obj);
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
                        ex.sendResponseHeaders(400, RES_400("Missing parameter instance").length());
                        os.write(RES_400("Missing parameter instance").getBytes());
                        os.close();
                        return;
                    }

                    if (params.containsKey("instance")) {
                        inst = Lightning.parse(chosen.getDeclaringClass(), params.get("instance"));
                    }

                    if (inst == null && !(Modifier.isStatic(chosen.getModifiers()))) {
                        ex.sendResponseHeaders(400, RES_400("Malformed parameter instance").length());
                        os.write(RES_400("Malformed parameter instance").getBytes());
                        os.close();
                        return;
                    }

                    try {
                        chosen.setAccessible(true);
                        Object res;
                        if (Modifier.isStatic(chosen.getModifiers())) {
                            if (args.size() == 0) {
                                res = chosen.invoke(null);
                            } else {
                                res = chosen.invoke(null, args.toArray());
                            }
                        } else {
                            if (args.size() == 0) {
                                res = chosen.invoke(inst);
                            } else {
                                res = chosen.invoke(inst, args.toArray());
                            }
                        }
                        String response = gson.toJson(new OtherRes(200, res));
                        ex.sendResponseHeaders(200, response.length());
                        os.write(response.getBytes());
                        os.close();
                    } catch (Exception e) {
                        String res = gson.toJson(new ExceptionRes(400, e.getClass().getName(), e.getMessage()));
                        ex.sendResponseHeaders(400, res.length());
                        os.write(res.getBytes());
                        os.close();
                    }
                } catch (NoSuchMethodException e) {
                    ex.sendResponseHeaders(400, RES_400("No method " + method).length());
                    os.write(RES_400("No method " + method).getBytes());
                    os.close();
                    return;   
                }
            } else {
                ex.sendResponseHeaders(404, RES_404.length());
                os.write(RES_404.getBytes());
                os.close();
            }
        } catch (Exception e) {
            ex.sendResponseHeaders(404, RES_404.length());
            os.write(RES_404.getBytes());
            os.close();
        }
    }
    
    public static Map<String, String> queryToMap(String query) {
        if(query == null) {
            return new HashMap<>();
        }
        Map<String, String> result = new HashMap<>();

        if (query.contains("&")) {
            for (String param : query.split("&")) {
                String[] entry = param.split("=");
                if (entry.length > 1) {
                    result.put(entry[0], URLDecoder.decode(entry[1], Charset.forName("UTF-8")));
                } else {
                    result.put(entry[0], "");
                }
            }
        } else {
            String[] entry = query.split("=");
            if (entry.length > 1) {
                result.put(entry[0], URLDecoder.decode(entry[1], Charset.forName("UTF-8")));
            } else {
                result.put(entry[0], "");
            }
        } 
        return result;
    }    
}
