package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import java.io.ByteArrayInputStream;
import java.util.Base64;

/** Logo KSAWU23 Client Ana jako tekstura (dekodowana z LogoData). */
public final class Logo {
    public static Identifier id;

    public static void load() {
        try {
            StringBuilder sb = new StringBuilder();
            for (String p : LogoData.PARTS) sb.append(p);
            byte[] png = Base64.getDecoder().decode(sb.toString());
            NativeImage ni = NativeImage.read(new ByteArrayInputStream(png));
            Identifier i = Identifier.of("ksawu23", "logo");
            MinecraftClient.getInstance().getTextureManager().registerTexture(i, new NativeImageBackedTexture(ni));
            id = i;
        } catch (Exception e) { id = null; }
    }

    /** Rysuje logo w zadanej szerokosci (zachowuje proporcje); zwraca wysokosc albo 0, gdy brak loga. */
    public static int draw(net.minecraft.client.gui.DrawContext c, int x, int y, int w) {
        if (id == null) return 0;
        int h = w * LogoData.H / LogoData.W;
        c.drawTexture(net.minecraft.client.render.RenderLayer::getGuiTextured, id, x, y, 0f, 0f, w, h, w, h);
        return h;
    }
}
