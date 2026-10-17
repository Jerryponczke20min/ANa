package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.io.*;

/** Wlasne obrazki: .minecraft/ksawu23/gui.png (tlo GUI) i inventory.png (tlo ekwipunku). */
public final class Theme {
    public static Identifier bg, inv;

    static Identifier tex(String file, String id) {
        MinecraftClient mc = MinecraftClient.getInstance();
        File f = new File(mc.runDirectory, "ksawu23/" + file);
        if (!f.exists()) return null;
        try (InputStream in = new FileInputStream(f)) {
            NativeImage img = NativeImage.read(in);
            Identifier i = Identifier.of("ksawu23", id);
            mc.getTextureManager().registerTexture(i, new NativeImageBackedTexture(img));
            return i;
        } catch (Exception e) { return null; }
    }

    public static void load() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "ksawu23");
        dir.mkdirs();
        try {
            File readme = new File(dir, "JAK_UZYWAC.txt");
            if (!readme.exists()) java.nio.file.Files.writeString(readme.toPath(),
                "Wrzuc tutaj obrazki PNG:\n gui.png = tlo menu klienta\n inventory.png = tlo ekwipunku\n" +
                "Potem w grze: Prawy Ctrl -> Ustawienia -> Motyw -> Przeladuj obrazki.\n");
        } catch (Exception ignored) {}
        bg = tex("gui.png", "gui_bg");
        loadInvSrc();
    }

    public static java.awt.image.BufferedImage invSrc;
    static boolean invReplaced;
    static final Identifier INV_ID = Identifier.of("minecraft", "textures/gui/container/inventory.png");

    static void loadInvSrc() {
        File f = new File(MinecraftClient.getInstance().runDirectory, "ksawu23/inventory.png");
        try { invSrc = f.exists() ? javax.imageio.ImageIO.read(f) : null; } catch (Exception e) { invSrc = null; }
    }

    /** Podmienia teksture panelu ekwipunku na wlasny obrazek (z liniami slotow i przyciemnieniem). */
    public static void applyInventory() {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            if (invSrc == null || !Modules.theme.invOn.value) {
                if (invReplaced) {
                    mc.getTextureManager().registerTexture(INV_ID, new net.minecraft.client.texture.ResourceTexture(INV_ID));
                    invReplaced = false;
                }
                return;
            }
            java.awt.image.BufferedImage out = new java.awt.image.BufferedImage(256, 256, java.awt.image.BufferedImage.TYPE_INT_ARGB);
            java.awt.Graphics2D g = out.createGraphics();
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(invSrc, 0, 0, 176, 166, null);
            float dim = Modules.theme.invDim.value.floatValue();
            if (dim > 0) { g.setColor(new java.awt.Color(0, 0, 0, (int) (255 * dim))); g.fillRect(0, 0, 176, 166); }
            if (Modules.theme.invLines.value) {
                g.setColor(new java.awt.Color(255, 255, 255, 120));
                int[][] s = {{8, 8}, {8, 26}, {8, 44}, {8, 62}, {77, 62}, {98, 18}, {116, 18}, {98, 36}, {116, 36}, {154, 28}};
                for (int[] p : s) g.drawRect(p[0] - 1, p[1] - 1, 17, 17);
                for (int r = 0; r < 3; r++) for (int c = 0; c < 9; c++) g.drawRect(8 + c * 18 - 1, 84 + r * 18 - 1, 17, 17);
                for (int c = 0; c < 9; c++) g.drawRect(8 + c * 18 - 1, 141, 17, 17);
            }
            g.setColor(new java.awt.Color(0, 0, 0, 200));
            g.drawRect(0, 0, 175, 165);
            g.dispose();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(out, "png", bo);
            NativeImage ni = NativeImage.read(new java.io.ByteArrayInputStream(bo.toByteArray()));
            mc.getTextureManager().registerTexture(INV_ID, new NativeImageBackedTexture(ni));
            invReplaced = true;
        } catch (Exception ignored) {}
    }

    public static int color() {
        Modules.Motyw t = Modules.theme;
        switch (t.preset.value) {
            case "Tecza": return 0xFF000000 | (java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 6000) / 6000f, 0.65f, 1f) & 0xFFFFFF);
            case "Wisnia": return 0xFFFF8FC0;
            case "Ocean": return 0xFF30B0FF;
            case "Lava": return 0xFFFF5020;
            default: break;
        }
        return 0xFF000000 | (t.r.value.intValue() << 16) | (t.g.value.intValue() << 8) | t.b.value.intValue();
    }
}
