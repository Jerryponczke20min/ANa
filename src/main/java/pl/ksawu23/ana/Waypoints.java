package pl.ksawu23.ana;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.render.Camera;
import net.minecraft.text.Text;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import java.io.*;
import java.util.*;
import java.util.function.Consumer;

public final class Waypoints {
    public static class WP {
        public String name; public double x, y, z; public String dim = "overworld"; public boolean show = true;
    }

    public static final List<WP> LIST = new ArrayList<>();
    static final List<String> DIMS = List.of("overworld", "nether", "end");

    static File file() { return new File(MinecraftClient.getInstance().runDirectory, "ksawu23/waypoints.json"); }

    public static void save() {
        JsonArray a = new JsonArray();
        for (WP w : LIST) {
            JsonObject o = new JsonObject();
            o.addProperty("name", w.name); o.addProperty("x", w.x); o.addProperty("y", w.y); o.addProperty("z", w.z);
            o.addProperty("dim", w.dim); o.addProperty("show", w.show);
            a.add(o);
        }
        try {
            file().getParentFile().mkdirs();
            try (Writer wr = new FileWriter(file())) { new Gson().toJson(a, wr); }
        } catch (Exception ignored) {}
    }

    public static void load() {
        LIST.clear();
        File f = file();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            for (JsonElement e : JsonParser.parseReader(r).getAsJsonArray()) {
                JsonObject o = e.getAsJsonObject();
                WP w = new WP();
                w.name = o.get("name").getAsString(); w.x = o.get("x").getAsDouble(); w.y = o.get("y").getAsDouble();
                w.z = o.get("z").getAsDouble(); w.dim = o.get("dim").getAsString(); w.show = o.get("show").getAsBoolean();
                LIST.add(w);
            }
        } catch (Exception ignored) {}
    }

    /** "overworld", "nether" albo "end" */
    public static String curDim() {
        var mc = MinecraftClient.getInstance();
        if (mc.world == null) return "overworld";
        return mc.world.getRegistryKey().getValue().getPath().replace("the_", "");
    }

    /** Wspolrzedne waypointa przeliczone na aktualny wymiar (Overworld <-> Nether x8). */
    public static double[] toCur(WP w) {
        double x = w.x, z = w.z;
        String cur = curDim();
        if (w.dim.equals("overworld") && cur.equals("nether")) { x /= 8; z /= 8; }
        else if (w.dim.equals("nether") && cur.equals("overworld")) { x *= 8; z *= 8; }
        return new double[]{x, w.y, z};
    }

    static boolean visibleHere(WP w) {
        String cur = curDim();
        if (cur.equals("end") || w.dim.equals("end")) return cur.equals(w.dim);
        return true;
    }

    /** Rzutuje punkt swiata na ekran (wspolrzedne GUI). Zwraca null, jesli punkt jest za kamera. */
    public static double[] project(Vec3d p, int sw, int sh) {
        var mc = MinecraftClient.getInstance();
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d rel = p.subtract(cam.getPos());
        double yaw = Math.toRadians(cam.getYaw()), pitch = Math.toRadians(cam.getPitch());
        Vec3d fwd = new Vec3d(-Math.sin(yaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3d up = right.crossProduct(fwd);
        double z = rel.dotProduct(fwd);
        if (z < 0.05) return null;
        double fov = Math.toRadians(mc.options.getFov().getValue());
        double scale = (sh / 2.0) / Math.tan(fov / 2.0);
        double sx = sw / 2.0 + rel.dotProduct(right) / z * scale;
        double sy = sh / 2.0 - rel.dotProduct(up) / z * scale;
        return new double[]{sx, sy};
    }

    public static class Mod extends Module {
        final Setting.Bool hud = add(new Setting.Bool("Znaczniki na ekranie", true));
        final Setting.Bool list = add(new Setting.Bool("Lista po prawej", true));
        final Setting.Bool beam = add(new Setting.Bool("Wiazka w swiecie", true));
        final Setting.Num scale = add(new Setting.Num("Rozmiar napisow", 0.6, 2, 0.1, 1.0));
        final Setting.Bool open = add(new Setting.Bool("Otworz edytor (klik)", false));

        public Mod() { super("Waypoints", "HUD"); enabled = true; }

        public void onTick() {
            if (open.value) { open.value = false; mc.setScreen(new WaypointScreen()); }
        }

        public void onRender(WorldRenderContext c) {
            if (!beam.value || LIST.isEmpty()) return;
            Matrix4f m = Hacks.matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            int col = Theme.color();
            RenderUtil.draw(m, b -> {
                for (WP w : LIST) {
                    if (!w.show || !visibleHere(w)) continue;
                    double[] p = toCur(w);
                    Box box = new Box(p[0] - 0.5, p[1], p[2] - 0.5, p[0] + 0.5, p[1] + 2, p[2] + 0.5).offset(-cam.x, -cam.y, -cam.z);
                    RenderUtil.box(b, m, box, col);
                    RenderUtil.line(b, m, new Vec3d(p[0] - cam.x, p[1] - 128 - cam.y, p[2] - cam.z),
                            new Vec3d(p[0] - cam.x, p[1] + 256 - cam.y, p[2] - cam.z), col);
                }
            });
        }

        public void onHud(DrawContext d) {
            if (LIST.isEmpty()) return;
            int sw = d.getScaledWindowWidth(), sh = d.getScaledWindowHeight();
            int col = Theme.color();
            int listY = 40;
            Vec3d pp = mc.player.getPos();
            for (WP w : LIST) {
                if (!w.show || !visibleHere(w)) continue;
                double[] p = toCur(w);
                double dist = Math.sqrt(Math.pow(p[0] - pp.x, 2) + Math.pow(p[1] - pp.y, 2) + Math.pow(p[2] - pp.z, 2));
                String label = w.name + "  " + (int) dist + " kr.";
                if (hud.value) {
                    double[] s = project(new Vec3d(p[0], p[1] + 1, p[2]), sw, sh);
                    if (s != null) {
                        var m = d.getMatrices();
                        m.push();
                        m.translate((float) s[0], (float) s[1], 0);
                        m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
                        d.fill(-3, -3, 4, 4, col);
                        d.drawCenteredTextWithShadow(mc.textRenderer, label, 0, -13, 0xFFFFFFFF);
                        m.pop();
                    }
                }
                if (list.value) {
                    d.drawTextWithShadow(mc.textRenderer, label, sw - mc.textRenderer.getWidth(label) - 4, listY, col);
                    listY += 10;
                }
            }
        }
    }
}

class WaypointScreen extends Screen {
    String nameS = "", xs = "", ys = "", zs = "", dim = "overworld";
    int off;

    WaypointScreen() { super(Text.literal("Waypointy")); dim = Waypoints.curDim(); }

    TextFieldWidget field(int x, int y, int w, String init, Consumer<String> l) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(""));
        f.setMaxLength(32); f.setText(init); f.setChangedListener(l);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int cx = width / 2, y = 36;
        field(cx - 150, y, 300, nameS, s -> nameS = s);
        field(cx - 150, y + 34, 94, xs, s -> xs = s);
        field(cx - 50, y + 34, 94, ys, s -> ys = s);
        field(cx + 56, y + 34, 94, zs, s -> zs = s);
        addDrawableChild(ButtonWidget.builder(Text.literal("Wymiar: " + dim), b -> {
            dim = Waypoints.DIMS.get((Waypoints.DIMS.indexOf(dim) + 1) % 3);
            b.setMessage(Text.literal("Wymiar: " + dim));
        }).dimensions(cx - 150, y + 60, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Moja pozycja"), b -> {
            var p = client.player;
            if (p == null) return;
            xs = String.valueOf(p.getBlockX()); ys = String.valueOf(p.getBlockY()); zs = String.valueOf(p.getBlockZ());
            dim = Waypoints.curDim();
            clearAndInit();
        }).dimensions(cx + 4, y + 60, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Dodaj waypoint"), b -> {
            try {
                Waypoints.WP w = new Waypoints.WP();
                w.name = nameS.isBlank() ? "WP" + (Waypoints.LIST.size() + 1) : nameS.trim();
                w.x = Double.parseDouble(xs.trim()); w.y = Double.parseDouble(ys.trim()); w.z = Double.parseDouble(zs.trim());
                w.dim = dim;
                Waypoints.LIST.add(w);
                Waypoints.save();
                nameS = "";
                clearAndInit();
            } catch (Exception ignored) {}
        }).dimensions(cx - 150, y + 86, 300, 20).build());

        int ly = y + 120;
        int rows = Math.max(1, (height - ly - 10) / 22);
        for (int i = off; i < Math.min(Waypoints.LIST.size(), off + rows); i++) {
            Waypoints.WP w = Waypoints.LIST.get(i);
            int ry = ly + (i - off) * 22;
            addDrawableChild(ButtonWidget.builder(Text.literal(w.show ? "Ukryj" : "Pokaz"), b -> {
                w.show = !w.show; Waypoints.save(); clearAndInit();
            }).dimensions(cx + 56, ry, 46, 18).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Usun"), b -> {
                Waypoints.LIST.remove(w); Waypoints.save(); clearAndInit();
            }).dimensions(cx + 106, ry, 44, 18).build());
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double h, double v) {
        int n = Waypoints.LIST.size();
        off = Math.max(0, Math.min(Math.max(0, n - 1), off - (int) Math.signum(v)));
        clearAndInit();
        return true;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext c, int mx, int my, float dt) {
        super.render(c, mx, my, dt);
        int cx = width / 2, y = 36;
        c.drawCenteredTextWithShadow(textRenderer, "Waypointy (wpisz wspolrzedne i nazwe)", cx, 14, Theme.color());
        c.drawTextWithShadow(textRenderer, "Nazwa", cx - 150, y - 10, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "X", cx - 150, y + 24, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "Y", cx - 50, y + 24, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "Z", cx + 56, y + 24, 0xFFAAAAAA);
        int ly = y + 120;
        int rows = Math.max(1, (height - ly - 10) / 22);
        var pp = client.player;
        for (int i = off; i < Math.min(Waypoints.LIST.size(), off + rows); i++) {
            Waypoints.WP w = Waypoints.LIST.get(i);
            int ry = ly + (i - off) * 22;
            double[] cur = Waypoints.toCur(w);
            String dist = pp == null ? "" : "  " + (int) Math.sqrt(Math.pow(cur[0] - pp.getX(), 2) + Math.pow(cur[1] - pp.getY(), 2) + Math.pow(cur[2] - pp.getZ(), 2)) + " kr.";
            String txt = w.name + " [" + w.dim + "] " + (int) w.x + ", " + (int) w.y + ", " + (int) w.z;
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(txt, 200), cx - 150, ry + 1, w.show ? 0xFFFFFFFF : 0xFF777777);
            c.drawTextWithShadow(textRenderer, "w tym wymiarze: " + (int) cur[0] + ", " + (int) cur[2] + dist, cx - 150, ry + 10, 0xFF999999);
        }
        c.drawCenteredTextWithShadow(textRenderer, "Kolko myszy przewija liste. Przejscie Overworld <-> Nether przelicza sie samo (x8).", cx, height - 12, 0xFF777777);
    }
}
