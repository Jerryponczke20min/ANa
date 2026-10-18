package pl.ksawu23.ana;

import com.google.gson.*;
import java.util.*;

public abstract class Setting<T> {
    public final String name;
    public T value;

    Setting(String name, T value) { this.name = name; this.value = value; }

    public abstract JsonElement save();
    public abstract void load(JsonElement e);

    public static class Bool extends Setting<Boolean> {
        public Bool(String n, boolean v) { super(n, v); }
        public JsonElement save() { return new JsonPrimitive(value); }
        public void load(JsonElement e) { value = e.getAsBoolean(); }
    }

    public static class Num extends Setting<Double> {
        public final double min, max, step;
        public Num(String n, double min, double max, double step, double def) {
            super(n, def); this.min = min; this.max = max; this.step = step;
        }
        public JsonElement save() { return new JsonPrimitive(value); }
        public void load(JsonElement e) { value = Math.max(min, Math.min(max, e.getAsDouble())); }
    }

    public static class Mode extends Setting<String> {
        public final List<String> modes;
        public Mode(String n, List<String> modes, String def) { super(n, def); this.modes = modes; }
        public void cycle() { value = modes.get((modes.indexOf(value) + 1) % modes.size()); }
        public JsonElement save() { return new JsonPrimitive(value); }
        public void load(JsonElement e) { if (modes.contains(e.getAsString())) value = e.getAsString(); }
    }

    /** Zestaw blokow (id bez "minecraft:") wybierany z listy opcji. */
    public static class Blocks extends Setting<Set<String>> {
        public final List<String> options;
        public Blocks(String n, List<String> options, Set<String> def) { super(n, new HashSet<>(def)); this.options = options; }
        public void toggle(String id) { if (!value.remove(id)) value.add(id); }
        public JsonElement save() { JsonArray a = new JsonArray(); value.forEach(a::add); return a; }
        public void load(JsonElement e) { value.clear(); e.getAsJsonArray().forEach(x -> value.add(x.getAsString())); }
    }
}
