package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

/** Wlasne obrazki: .minecraft/ksawu23/gui.png|gui.gif (tlo GUI) i inventory.png|inventory.gif (wyglad ekwipunku). */
public final class Theme {
    public static Identifier bg, inv;
    public static BufferedImage invSrc;
    static final List<BufferedImage> invGif = new ArrayList<>();
    static final List<Integer> invGifDelays = new ArrayList<>();
    static final List<byte[]> invFrames = new ArrayList<>();
    static int invIdx;
    static long invLast;
    static boolean invReplaced;
    static final Identifier INV_ID = Identifier.of("minecraft", "textures/gui/container/inventory.png");

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
                "Najprosciej: otworz GUI (Prawy Ctrl) i przeciagnij obrazek (PNG/JPG) albo GIF na okno gry.\\n" +
                "Pliki: gui.png / gui.gif = tlo menu, inventory.png / inventory.gif = wyglad ekwipunku.\\n" +
                "Filmy MP4 zamien na GIF (np. ezgif.com/video-to-gif).\\n");
        } catch (Exception ignored) {}
        Logo.load();
        bg = tex("gui.png", "gui_bg");
        AnimBg.load();
        loadInvSrc();
    }

    static void loadInvSrc() {
        File dir = new File(MinecraftClient.getInstance().runDirectory, "ksawu23");
        File f = new File(dir, "inventory.png");
        try { invSrc = f.exists() ? ImageIO.read(f) : null; } catch (Exception e) { invSrc = null; }
        invGif.clear(); invGifDelays.clear();
        File g = new File(dir, "inventory.gif");
        if (g.exists()) {
            try { invGif.addAll(AnimBg.decode(g, invGifDelays, 24, 352)); } catch (Exception ignored) {}
        }
    }

    static BufferedImage composeInv(BufferedImage src) {
        BufferedImage out = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = out.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(src, 0, 0, 176, 166, null);
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
        return out;
    }

    static byte[] png(BufferedImage img) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        ImageIO.write(img, "png", bo);
        return bo.toByteArray();
    }

    static void registerInv(byte[] bytes) throws Exception {
        NativeImage ni = NativeImage.read(new ByteArrayInputStream(bytes));
        MinecraftClient.getInstance().getTextureManager().registerTexture(INV_ID, new NativeImageBackedTexture(ni));
    }

    /** Podmienia teksture panelu ekwipunku (obrazek albo animowany GIF). */
    public static void applyInventory() {
        MinecraftClient mc = MinecraftClient.getInstance();
        try {
            boolean any = invSrc != null || !invGif.isEmpty();
            if (!any || !Modules.theme.invOn.value) {
                if (invReplaced) {
                    mc.getTextureManager().registerTexture(INV_ID, new net.minecraft.client.texture.ResourceTexture(INV_ID));
                    invReplaced = false;
                }
                return;
            }
            invFrames.clear();
            if (!invGif.isEmpty()) for (BufferedImage f : invGif) invFrames.add(png(composeInv(f)));
            else invFrames.add(png(composeInv(invSrc)));
            invIdx = 0;
            invLast = System.currentTimeMillis();
            registerInv(invFrames.get(0));
            invReplaced = true;
        } catch (Exception ignored) {}
    }

    /** Wywolywane co klatke przy otwartym ekwipunku: kolejna klatka GIF-a. */
    public static void tickInventory() {
        if (!invReplaced || invFrames.size() < 2) return;
        long now = System.currentTimeMillis();
        int d = invGifDelays.isEmpty() ? 100 : invGifDelays.get(Math.min(invIdx, invGifDelays.size() - 1));
        if (now - invLast < d) return;
        invLast = now;
        invIdx = (invIdx + 1) % invFrames.size();
        try { registerInv(invFrames.get(invIdx)); } catch (Exception ignored) {}
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
