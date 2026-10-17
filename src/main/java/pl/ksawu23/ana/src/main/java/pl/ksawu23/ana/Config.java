package pl.ksawu23.ana;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import java.io.*;

public final class Config {
    static final Gson G = new GsonBuilder().setPrettyPrinting().create();

    static File file() {
        File f = new File(MinecraftClient.getInstance().runDirectory, "config/ksawu23.json");
        f.getParentFile().mkdirs();
        return f;
    }

    public static void save() {
        JsonObject root = new JsonObject();
        for (Module m : Modules.ALL) {
            JsonObject o = new JsonObject();
            o.addProperty("enabled", m.enabled);
            o.addProperty("key", m.key);
            JsonObject s = new JsonObject();
            for (Setting<?> st : m.settings) s.add(st.name, st.save());
            o.add("settings", s);
            root.add(m.name, o);
        }
        try (Writer w = new FileWriter(file())) { G.toJson(root, w); } catch (Exception ignored) {}
    }

    public static void load() {
        File f = file();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
            for (Module m : Modules.ALL) {
                if (!root.has(m.name)) continue;
                JsonObject o = root.getAsJsonObject(m.name);
                if (!m.alwaysOn && o.has("enabled")) m.enabled = o.get("enabled").getAsBoolean();
                if (o.has("key")) m.key = o.get("key").getAsInt();
                JsonObject s = o.has("settings") ? o.getAsJsonObject("settings") : new JsonObject();
                for (Setting<?> st : m.settings) {
                    try { if (s.has(st.name)) st.load(s.get(st.name)); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
    }
}
