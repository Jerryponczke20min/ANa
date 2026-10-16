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
        new File(MinecraftClient.getInstance().runDirectory, "ksawu23").mkdirs();
        bg = tex("gui.png", "gui_bg");
        inv = tex("inventory.png", "inv_bg");
    }

    public static int color() {
        Modules.Motyw t = Modules.theme;
        return 0xFF000000 | (t.r.value.intValue() << 16) | (t.g.value.intValue() << 8) | t.b.value.intValue();
    }
}
