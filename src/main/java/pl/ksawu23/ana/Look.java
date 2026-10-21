package pl.ksawu23.ana;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.*;

/** Dodatki do wygladu: niebo, pack maker, czasteczki, china hat, jump circles, obrys bloku. */
public final class Look {

    static int hue(double speed, float sat) {
        return java.awt.Color.HSBtoRGB((float) ((System.currentTimeMillis() * speed / 8000.0) % 1.0), sat, 1f) & 0xFFFFFF;
    }

    static int rainbowAt(double frac) {
        return java.awt.Color.HSBtoRGB((float) ((frac + System.currentTimeMillis() / 4000.0) % 1.0), 0.7f, 1f) & 0xFFFFFF;
    }

    static int colorByStyle(String style, double frac) {
        switch (style) {
            case "Tecza": return rainbowAt(frac);
            case "Wisnia": return ClickGui.lerp(0xFFFFC2DA, 0xFFFF7FB0, (float) (Math.sin(frac * 6.28) * 0.5 + 0.5)) & 0xFFFFFF;
            default: return Theme.color() & 0xFFFFFF;
        }
    }

    public static class Sky extends Module {
        final Setting.Mode style = add(new Setting.Mode("Styl", List.of("Kolor", "Tecza", "Wisnia", "Zachod", "Fiolet", "Zmierzch", "Noc", "Mietowy"), "Kolor"));
        final Setting.Num r = add(new Setting.Num("R", 0, 255, 1, 120));
        final Setting.Num g = add(new Setting.Num("G", 0, 255, 1, 170));
        final Setting.Num b = add(new Setting.Num("B", 0, 255, 1, 255));
        final Setting.Num speed = add(new Setting.Num("Predkosc teczy", 0.2, 5, 0.1, 1));

        public Sky() { super("Kolor nieba", "Visuals"); }

        public int rgb() {
            switch (style.value) {
                case "Tecza": return hue(speed.value, 0.55f);
                case "Wisnia": return 0xFFB7D5;
                case "Zachod": return 0xFF7A4A;
                case "Fiolet": return 0x6A3FB5;
                case "Zmierzch": return 0x3A2A6B;
                case "Noc": return 0x0A0F2E;
                case "Mietowy": return 0x7FFFD4;
                default: return (r.value.intValue() << 16) | (g.value.intValue() << 8) | b.value.intValue();
            }
        }
    }

    /** Texture Pack Maker: generuje paczke zasobow .minecraft/resourcepacks/KSAWU23-Items. */
    public static class ItemSkins extends Module {
        public ItemSkins() {
            super("Texture Pack Maker", "Visuals");
            alwaysOn = true; enabled = true;
            action = () -> mc.setScreen(new ItemSkinScreen());
        }
    }

    public static class ParticleTrail extends Module {
        final Setting.Mode type = add(new Setting.Mode("Czasteczka", Util.PARTICLES, "CHERRY"));
        final Setting.Mode style = add(new Setting.Mode("Styl", List.of("Slad", "Aura", "Spirala"), "Aura"));
        final Setting.Num density = add(new Setting.Num("Gestosc", 1, 12, 1, 4));
        double ang;

        public ParticleTrail() { super("Slad czasteczek", "Visuals"); }

        public void onTick() {
            var p = mc.player;
            ParticleEffect pe = Util.particle(type.value);
            int n = density.value.intValue();
            ang += 0.25;
            switch (style.value) {
                case "Slad" -> {
                    if (p.getVelocity().horizontalLengthSquared() < 0.0004) return;
                    for (int i = 0; i < n; i++)
                        mc.world.addParticle(pe, p.getX() + (Math.random() - 0.5) * 0.5, p.getY() + 0.1, p.getZ() + (Math.random() - 0.5) * 0.5, 0, 0.02, 0);
                }
                case "Spirala" -> {
                    for (int i = 0; i < n; i++) {
                        double a = ang + i * Math.PI * 2 / n, y = (ang * 0.15 + i * 0.3) % 2.0;
                        mc.world.addParticle(pe, p.getX() + Math.cos(a) * 0.7, p.getY() + y, p.getZ() + Math.sin(a) * 0.7, 0, 0, 0);
                    }
                }
                default -> {
                    for (int i = 0; i < n; i++) {
                        double a = ang + i * Math.PI * 2 / n;
                        mc.world.addParticle(pe, p.getX() + Math.cos(a) * 0.8, p.getY() + 1 + Math.sin(ang * 2 + i) * 0.5, p.getZ() + Math.sin(a) * 0.8, 0, 0, 0);
                    }
                }
            }
        }
    }

    /** Czasteczki spadajace z nieba wokol Ciebie (platki wisni, snieg, iskry...). */
    public static class FallingParticles extends Module {
        final Setting.Mode type = add(new Setting.Mode("Czasteczka", Util.PARTICLES, "CHERRY"));
        final Setting.Num radius = add(new Setting.Num("Promien", 4, 30, 1, 14));
        final Setting.Num height = add(new Setting.Num("Wysokosc", 6, 30, 1, 14));
        final Setting.Num density = add(new Setting.Num("Gestosc", 1, 40, 1, 10));

        public FallingParticles() { super("Opady czasteczek", "Visuals"); }

        public void onTick() {
            var p = mc.player;
            ParticleEffect pe = Util.particle(type.value);
            for (int i = 0; i < density.value.intValue(); i++) {
                double x = p.getX() + (Math.random() * 2 - 1) * radius.value;
                double z = p.getZ() + (Math.random() * 2 - 1) * radius.value;
                double y = p.getY() + height.value * (0.6 + 0.4 * Math.random());
                mc.world.addParticle(pe, x, y, z, (Math.random() - 0.5) * 0.02, -0.05, (Math.random() - 0.5) * 0.02);
            }
        }
    }

    /** Chinski kapelusz nad glowa (widoczny w trzeciej osobie i we Freecamie). */
    public static class ChinaHat extends Module {
        final Setting.Mode style = add(new Setting.Mode("Kolor", List.of("Tecza", "Wisnia", "Motyw"), "Tecza"));
        final Setting.Num radius = add(new Setting.Num("Promien", 0.3, 1.0, 0.05, 0.6));
        final Setting.Num height = add(new Setting.Num("Wysokosc", 0.1, 0.8, 0.05, 0.3));
        final Setting.Num segments = add(new Setting.Num("Segmenty", 8, 48, 2, 28));
        final Setting.Bool others = add(new Setting.Bool("Tez u innych graczy", false));

        public ChinaHat() { super("China Hat", "Visuals"); }

        public void onRender(WorldRenderContext c) {
            Matrix4f m = Hacks.matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            float td = c.tickCounter().getTickDelta(false);
            int seg = segments.value.intValue();
            double rad = radius.value, hh = height.value;
            RenderUtil.draw(m, b -> {
                for (PlayerEntity p : mc.world.getPlayers()) {
                    boolean selfP = p == mc.player || p == mc.getCameraEntity();
                    if (!selfP && !others.value) continue;
                    if (selfP && mc.getCameraEntity() == mc.player && mc.options.getPerspective().isFirstPerson()) continue;
                    Vec3d base = p.getLerpedPos(td).add(0, p.getHeight() + 0.02, 0).subtract(cam);
                    Vec3d apex = base.add(0, hh, 0);
                    Vec3d prev = null;
                    for (int i = 0; i <= seg; i++) {
                        double a = i * Math.PI * 2 / seg;
                        Vec3d pt = base.add(Math.cos(a) * rad, 0, Math.sin(a) * rad);
                        int col = 0xFF000000 | colorByStyle(style.value, (double) i / seg);
                        if (prev != null) RenderUtil.line(b, m, prev, pt, col);
                        if (i % 2 == 0) RenderUtil.line(b, m, apex, pt, col);
                        prev = pt;
                    }
                }
            });
        }
    }

    /** Kregi pod nogami przy skoku: rozszerzajacy sie, zanikajacy pierscien. */
    public static class JumpCircles extends Module {
        final Setting.Mode style = add(new Setting.Mode("Kolor", List.of("Tecza", "Wisnia", "Motyw"), "Tecza"));
        final Setting.Num size = add(new Setting.Num("Rozmiar", 0.5, 4, 0.1, 1.6));
        final Setting.Num life = add(new Setting.Num("Czas (tick)", 10, 60, 1, 25));
        final List<double[]> rings = new ArrayList<>();
        boolean wasGround;

        public JumpCircles() { super("Jump Circles", "Visuals"); }

        public void onTick() {
            boolean g = mc.player.isOnGround();
            if (wasGround && !g && mc.player.getVelocity().y > 0.1)
                rings.add(new double[]{mc.player.getX(), mc.player.getY() + 0.03, mc.player.getZ(), 0});
            wasGround = g;
            rings.removeIf(r -> ++r[3] > life.value);
        }

        public void onDisable() { rings.clear(); }

        public void onRender(WorldRenderContext c) {
            if (rings.isEmpty()) return;
            Matrix4f m = Hacks.matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            RenderUtil.draw(m, b -> {
                for (double[] r : rings) {
                    double f = r[3] / life.value;
                    double rad = size.value * (0.2 + 0.8 * f);
                    int alpha = (int) (255 * (1 - f));
                    Vec3d prev = null;
                    for (int i = 0; i <= 36; i++) {
                        double a = i * Math.PI * 2 / 36;
                        Vec3d pt = new Vec3d(r[0] + Math.cos(a) * rad - cam.x, r[1] - cam.y, r[2] + Math.sin(a) * rad - cam.z);
                        int col = (alpha << 24) | colorByStyle(style.value, i / 36.0);
                        if (prev != null) RenderUtil.line(b, m, prev, pt, col);
                        prev = pt;
                    }
                }
            });
        }
    }

    public static class BlockOutline extends Module {
        final Setting.Mode style = add(new Setting.Mode("Kolor", List.of("Motyw", "Tecza", "Wisnia", "Bialy"), "Tecza"));
        public BlockOutline() { super("Obrys bloku", "Visuals"); }

        public void onRender(WorldRenderContext c) {
            if (!(mc.crosshairTarget instanceof BlockHitResult bhr) || bhr.getType() != HitResult.Type.BLOCK) return;
            Matrix4f m = Hacks.matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            int rgb = switch (style.value) {
                case "Tecza" -> hue(1.5, 0.7f);
                case "Wisnia" -> 0xFF8FC0;
                case "Bialy" -> 0xFFFFFF;
                default -> Theme.color() & 0xFFFFFF;
            };
            Box box = new Box(bhr.getBlockPos()).expand(0.003).offset(-cam.x, -cam.y, -cam.z);
            RenderUtil.draw(m, b -> RenderUtil.box(b, m, box, 0xFF000000 | rgb));
        }
    }
}

class ItemSkinScreen extends Screen {
    static final List<String> TYPES = List.of("Item", "Blok", "Slonce", "Ksiezyc", "Chmury", "Celownik", "Hotbar", "Zaznaczenie hotbara", "Niebo (skybox)");
    static final List<String> OVERLAYS = List.of("Maska + cieniowanie", "Maska (czysty obrazek)", "Kwadrat");
    static final int[] RES = {16, 32, 64, 128};
    static String type = "Item", overlay = OVERLAYS.get(0), itemS = "diamond_sword", rS = "255", gS = "120", bS = "0", status = "";
    static boolean recolor = true;
    static int resIdx = 2;

    ItemSkinScreen() { super(Text.literal("Texture Pack Maker")); }

    static File packDir() { return new File(MinecraftClient.getInstance().runDirectory, "resourcepacks/KSAWU23-Items"); }

    static String path() {
        String id = itemS.trim().toLowerCase();
        return switch (type) {
            case "Item" -> "textures/item/" + id + ".png";
            case "Blok" -> "textures/block/" + id + ".png";
            case "Slonce" -> "textures/environment/sun.png";
            case "Ksiezyc" -> "textures/environment/moon_phases.png";
            case "Chmury" -> "textures/environment/clouds.png";
            case "Celownik" -> "textures/gui/sprites/hud/crosshair.png";
            case "Hotbar" -> "textures/gui/sprites/hud/hotbar.png";
            case "Niebo (skybox)" -> "optifine/sky/world0/ksawu23_sky.png";
            default -> "textures/gui/sprites/hud/hotbar_selection.png";
        };
    }

    static int[] size() {
        return switch (type) {
            case "Ksiezyc" -> new int[]{128, 64};
            case "Chmury" -> new int[]{256, 256};
            case "Celownik" -> new int[]{15, 15};
            case "Hotbar" -> new int[]{182, 22};
            case "Zaznaczenie hotbara" -> new int[]{24, 23};
            default -> new int[]{32, 32};
        };
    }

    static File target() { return new File(packDir(), "assets/minecraft/" + path()); }

    static void ensurePack() throws Exception {
        File d = packDir();
        d.mkdirs();
        File mcmeta = new File(d, "pack.mcmeta");
        if (!mcmeta.exists()) java.nio.file.Files.writeString(mcmeta.toPath(), "{\"pack\":{\"pack_format\":46,\"description\":\"KSAWU23 Texture Pack\"}}");
        target().getParentFile().mkdirs();
    }

    static int num(String s) {
        try { return Math.max(0, Math.min(255, Integer.parseInt(s.trim()))); } catch (Exception e) { return 255; }
    }

    static boolean valid() {
        String id = itemS.trim().toLowerCase();
        if (type.equals("Item")) return Registries.ITEM.containsId(Identifier.of("minecraft", id));
        if (type.equals("Blok")) return Registries.BLOCK.containsId(Identifier.of("minecraft", id));
        return true;
    }

    /** Oryginalna tekstura z gry (albo null). */
    static BufferedImage vanilla(String p) {
        try (InputStream in = MinecraftClient.getInstance().getResourceManager()
                .getResource(Identifier.of("minecraft", p)).get().getInputStream()) {
            return ImageIO.read(in);
        } catch (Exception e) { return null; }
    }

    /** Naklada obrazek na ksztalt itemu: przezroczystosc z oryginalu, kolory z obrazka (opcjonalnie z cieniowaniem). */
    static BufferedImage compose(BufferedImage frame, BufferedImage mask, int res) {
        BufferedImage sc = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = sc.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(frame, 0, 0, res, res, null);
        g.dispose();
        if (mask == null) return sc;
        int mw = mask.getWidth(), mh = Math.min(mask.getHeight(), mask.getWidth());
        boolean shade = overlay.startsWith("Maska +");
        BufferedImage out = new BufferedImage(res, res, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < res; x++) for (int y = 0; y < res; y++) {
            int mp = mask.getRGB(x * mw / res, y * mh / res);
            int a = mp >>> 24;
            if (a == 0) continue;
            int ip = sc.getRGB(x, y);
            int r = (ip >> 16) & 255, gg = (ip >> 8) & 255, b = ip & 255;
            if (shade) {
                float l = (0.299f * ((mp >> 16) & 255) + 0.587f * ((mp >> 8) & 255) + 0.114f * (mp & 255)) / 255f;
                float f = 0.5f + 0.6f * l;
                r = Math.min(255, (int) (r * f)); gg = Math.min(255, (int) (gg * f)); b = Math.min(255, (int) (b * f));
            }
            out.setRGB(x, y, (a << 24) | (r << 16) | (gg << 8) | b);
        }
        return out;
    }

    TextFieldWidget field(int x, int y, int w, String init, java.util.function.Consumer<String> l) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(""));
        f.setMaxLength(48); f.setText(init); f.setChangedListener(l);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        field(cx - 150, 40, 300, itemS, s -> itemS = s);
        field(cx - 150, 76, 94, rS, s -> rS = s);
        field(cx - 50, 76, 94, gS, s -> gS = s);
        field(cx + 56, 76, 94, bS, s -> bS = s);
        addDrawableChild(ButtonWidget.builder(Text.literal("Typ: " + type), b -> {
            type = TYPES.get((TYPES.indexOf(type) + 1) % TYPES.size()); clearAndInit();
        }).dimensions(cx - 150, 102, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Kolor: " + (recolor ? "Przemalowanie" : "Mnozenie")), b -> {
            recolor = !recolor; clearAndInit();
        }).dimensions(cx + 4, 102, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Naklad.: " + overlay), b -> {
            overlay = OVERLAYS.get((OVERLAYS.indexOf(overlay) + 1) % OVERLAYS.size()); clearAndInit();
        }).dimensions(cx - 150, 126, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Rozdzielczosc: " + RES[resIdx]), b -> {
            resIdx = (resIdx + 1) % RES.length; clearAndInit();
        }).dimensions(cx + 4, 126, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zastosuj kolor"), b -> applyColor())
                .dimensions(cx - 150, 150, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Usun ten skin"), b -> deleteSkin())
                .dimensions(cx + 4, 150, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Przeladuj zasoby"), b -> {
            status = "Przeladowuje zasoby...";
            MinecraftClient.getInstance().reloadResources();
        }).dimensions(cx - 150, 174, 300, 20).build());
    }

    void deleteSkin() {
        File t = target();
        boolean ok = t.delete();
        new File(t.getParentFile(), t.getName() + ".mcmeta").delete();
        if (type.equals("Niebo (skybox)")) {
            new File(t.getParentFile(), "ksawu23_sky.properties").delete();
            File mp = new File(packDir(), "assets/minecraft/mcpatcher/sky/world0");
            new File(mp, "ksawu23_sky.png").delete();
            new File(mp, "ksawu23_sky.properties").delete();
        }
        status = ok ? "Usunieto: " + path() : "Brak skina dla: " + path();
    }

    void applyColor() {
        try {
            if (!valid()) { status = "Nie ma takiego " + (type.equals("Item") ? "itemu" : "bloku") + ": " + itemS; return; }
            BufferedImage src = vanilla(path());
            if (src == null) { status = "Brak oryginalnej tekstury: " + path(); return; }
            int cr = num(rS), cg = num(gS), cb = num(bS);
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int x = 0; x < src.getWidth(); x++) for (int y = 0; y < src.getHeight(); y++) {
                int p = src.getRGB(x, y), a = p >>> 24;
                if (a == 0) { out.setRGB(x, y, 0); continue; }
                int r = (p >> 16) & 255, g = (p >> 8) & 255, b = p & 255;
                if (recolor) {
                    float l = (0.299f * r + 0.587f * g + 0.114f * b) / 255f * 1.3f;
                    r = (int) Math.min(255, cr * l); g = (int) Math.min(255, cg * l); b = (int) Math.min(255, cb * l);
                } else { r = r * cr / 255; g = g * cg / 255; b = b * cb / 255; }
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
            ensurePack();
            ImageIO.write(out, "png", target());
            status = "Zapisano: " + path() + ". Kliknij 'Przeladuj zasoby'.";
        } catch (Exception e) { status = "Blad: " + e.getMessage(); }
    }

    void writeSky(BufferedImage img) throws Exception {
        int t = 512;
        BufferedImage atlas = new BufferedImage(t * 3, t * 2, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = atlas.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        for (int i = 0; i < 6; i++) g.drawImage(img, (i % 3) * t, (i / 3) * t, t, t, null);
        g.dispose();
        String props = "source=./ksawu23_sky.png\nblend=replace\nrotate=false\nstartFadeIn=0:00\nendFadeIn=0:01\nstartFadeOut=23:58\nendFadeOut=23:59\n";
        File dir = target().getParentFile();
        ImageIO.write(atlas, "png", target());
        java.nio.file.Files.writeString(new File(dir, "ksawu23_sky.properties").toPath(), props);
        File mp = new File(packDir(), "assets/minecraft/mcpatcher/sky/world0");
        mp.mkdirs();
        ImageIO.write(atlas, "png", new File(mp, "ksawu23_sky.png"));
        java.nio.file.Files.writeString(new File(mp, "ksawu23_sky.properties").toPath(), props);
    }

    void applyImage(Path path) {
        try {
            String fn = path.getFileName().toString().toLowerCase();
            if (fn.endsWith(".mp4") || fn.endsWith(".webm") || fn.endsWith(".mov") || fn.endsWith(".avi") || fn.endsWith(".mkv")) {
                status = "Filmy nie sa obslugiwane - zamien film na GIF (np. ezgif.com/video-to-gif).";
                return;
            }
            if (!valid()) { status = "Najpierw wpisz poprawna nazwe (np. diamond_sword / stone)"; return; }
            List<Integer> delays = new ArrayList<>();
            List<BufferedImage> frames = new ArrayList<>();
            if (fn.endsWith(".gif")) frames = AnimBg.decode(path.toFile(), delays, 24, 256);
            if (frames.isEmpty()) {
                BufferedImage img = ImageIO.read(path.toFile());
                if (img == null) { status = "To nie jest obrazek"; return; }
                frames.add(img);
            }
            ensurePack();
            File t = target();
            int res = RES[resIdx];
            if (type.equals("Item") || type.equals("Blok")) {
                BufferedImage mask = overlay.equals("Kwadrat") ? null : vanilla(path());
                BufferedImage strip = new BufferedImage(res, res * frames.size(), BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = strip.createGraphics();
                for (int i = 0; i < frames.size(); i++) g.drawImage(compose(frames.get(i), mask, res), 0, i * res, null);
                g.dispose();
                ImageIO.write(strip, "png", t);
                File meta = new File(t.getParentFile(), t.getName() + ".mcmeta");
                if (frames.size() > 1) {
                    int avg = delays.stream().mapToInt(Integer::intValue).sum() / Math.max(1, delays.size());
                    int ft = Math.max(1, Math.round(avg / 50f));
                    java.nio.file.Files.writeString(meta.toPath(), "{\"animation\":{\"frametime\":" + ft + "}}");
                } else meta.delete();
                status = (mask == null ? "Obrazek (kwadrat)" : "Obrazek nalozony na ksztalt") + " -> " + path() + (frames.size() > 1 ? " (animowany)" : "") + ". Przeladuj zasoby.";
            } else if (type.equals("Niebo (skybox)")) {
                writeSky(frames.get(0));
                status = "Skybox zapisany (wymaga moda Nuit / FabricSkyBoxes). Przeladuj zasoby.";
            } else {
                int[] sz = size();
                BufferedImage out = new BufferedImage(sz[0], sz[1], BufferedImage.TYPE_INT_ARGB);
                java.awt.Graphics2D g = out.createGraphics();
                g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                if (type.equals("Ksiezyc")) {
                    for (int i = 0; i < 8; i++) g.drawImage(frames.get(0), (i % 4) * 32, (i / 4) * 32, 32, 32, null);
                } else g.drawImage(frames.get(0), 0, 0, sz[0], sz[1], null);
                g.dispose();
                ImageIO.write(out, "png", t);
                status = "Obrazek zapisany jako " + path() + ". Przeladuj zasoby.";
            }
        } catch (Exception e) { status = "Blad: " + e.getMessage(); }
    }

    public void onFilesDropped(List<Path> paths) { filesDragged(paths); }

    public void filesDragged(List<Path> paths) { if (!paths.isEmpty()) applyImage(paths.get(0)); }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        super.render(c, mx, my, dt);
        int cx = width / 2;
        boolean needsName = type.equals("Item") || type.equals("Blok");
        c.drawCenteredTextWithShadow(textRenderer, "Texture Pack Maker - kolor albo wlasny obrazek / GIF", cx, 14, Theme.color());
        c.drawTextWithShadow(textRenderer, needsName ? "Nazwa (np. diamond_sword, golden_apple, stone)" : "Nazwa niepotrzebna dla tego typu", cx - 150, 30, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "R", cx - 150, 66, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "G", cx - 50, 66, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "B", cx + 56, 66, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(textRenderer, "Przeciagnij PNG/JPG/GIF na to okno = obrazek jako tekstura (typ: " + type + ")", cx, 204, 0xFFCCCCCC);
        c.drawCenteredTextWithShadow(textRenderer, "Nakladka: obrazek przyjmuje ksztalt itemu (np. miecza). GIF = animowana tekstura.", cx, 218, 0xFF999999);
        c.drawCenteredTextWithShadow(textRenderer, "Plik: " + path(), cx, 232, 0xFF888888);
        c.drawCenteredTextWithShadow(textRenderer, "Paczka KSAWU23-Items: wlacz raz w Opcje > Paczki zasobow (przenies na gore).", cx, 246, 0xFF888888);
        if (!status.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, status, cx, 264, 0xFF55FF55);
    }
}
