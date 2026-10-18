package pl.ksawu23.ana;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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

/** Dodatki do wygladu: kolor nieba, skiny itemow, slad czasteczek, obrys bloku. */
public final class Look {

    static int hue(double speed, float sat) {
        return java.awt.Color.HSBtoRGB((float) ((System.currentTimeMillis() * speed / 8000.0) % 1.0), sat, 1f) & 0xFFFFFF;
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

    /** Skiny itemow: generuje paczke zasobow .minecraft/resourcepacks/KSAWU23-Items. */
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
    static final List<String> TYPES = List.of("Item", "Blok", "Slonce", "Ksiezyc", "Chmury", "Celownik", "Hotbar", "Zaznaczenie hotbara");
    static String type = "Item", itemS = "diamond_sword", rS = "255", gS = "120", bS = "0", status = "";
    static boolean recolor = true;

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
        addDrawableChild(ButtonWidget.builder(Text.literal("Tryb: " + (recolor ? "Przemalowanie" : "Mnozenie")), b -> {
            recolor = !recolor; clearAndInit();
        }).dimensions(cx + 4, 102, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zastosuj kolor"), b -> applyColor())
                .dimensions(cx - 150, 126, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Usun ten skin"), b -> {
            status = target().delete() ? "Usunieto: " + path() : "Brak skina dla: " + path();
        }).dimensions(cx + 4, 126, 146, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Przeladuj zasoby"), b -> {
            status = "Przeladowuje zasoby...";
            MinecraftClient.getInstance().reloadResources();
        }).dimensions(cx - 150, 150, 300, 20).build());
    }

    void applyColor() {
        try {
            if (!valid()) { status = "Nie ma takiego " + (type.equals("Item") ? "itemu" : "bloku") + ": " + itemS; return; }
            BufferedImage src;
            try (InputStream in = MinecraftClient.getInstance().getResourceManager()
                    .getResource(Identifier.of("minecraft", path())).get().getInputStream()) {
                src = ImageIO.read(in);
            } catch (Exception e) { status = "Brak oryginalnej tekstury: " + path(); return; }
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

    void applyImage(Path path) {
        try {
            if (!valid()) { status = "Najpierw wpisz poprawna nazwe (np. diamond_sword / stone)"; return; }
            BufferedImage img = ImageIO.read(path.toFile());
            if (img == null) { status = "To nie jest obrazek"; return; }
            int[] sz = size();
            BufferedImage out = new BufferedImage(sz[0], sz[1], BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            if (type.equals("Ksiezyc")) {
                for (int i = 0; i < 8; i++) g.drawImage(img, (i % 4) * 32, (i / 4) * 32, 32, 32, null);
            } else g.drawImage(img, 0, 0, sz[0], sz[1], null);
            g.dispose();
            ensurePack();
            ImageIO.write(out, "png", target());
            status = "Obrazek zapisany jako " + path() + ". Kliknij 'Przeladuj zasoby'.";
        } catch (Exception e) { status = "Blad: " + e.getMessage(); }
    }

    public void onFilesDropped(List<Path> paths) { filesDragged(paths); }

    public void filesDragged(List<Path> paths) { if (!paths.isEmpty()) applyImage(paths.get(0)); }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        super.render(c, mx, my, dt);
        int cx = width / 2;
        boolean needsName = type.equals("Item") || type.equals("Blok");
        c.drawCenteredTextWithShadow(textRenderer, "Texture Pack Maker - kolor albo wlasny obrazek", cx, 14, Theme.color());
        c.drawTextWithShadow(textRenderer, needsName ? "Nazwa (np. diamond_sword, golden_apple, stone)" : "Nazwa niepotrzebna dla tego typu", cx - 150, 30, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "R", cx - 150, 66, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "G", cx - 50, 66, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "B", cx + 56, 66, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(textRenderer, "Przeciagnij PNG/JPG na to okno = obrazek jako tekstura (typ: " + type + ")", cx, 180, 0xFFCCCCCC);
        c.drawCenteredTextWithShadow(textRenderer, "Plik: " + path(), cx, 194, 0xFF888888);
        c.drawCenteredTextWithShadow(textRenderer, "Paczka KSAWU23-Items: wlacz raz w Opcje > Paczki zasobow (przenies na gore).", cx, 208, 0xFF888888);
        if (!status.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, status, cx, 226, 0xFF55FF55);
    }
}
