package pl.ksawu23.ana;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.util.*;
import java.util.function.Consumer;

public class ClickGui extends Screen {
    static final String[] CATS = {"Anarchia", "Combat", "Visuals", "HUD", "Ustawienia"};

    record Hit(int x, int y, int w, int h, Runnable l, Runnable r, Runnable m, Consumer<Double> drag) {
        boolean in(double a, double b) { return a >= x && a < x + w && b >= y && b < y + h; }
    }

    final List<Hit> hits = new ArrayList<>();
    Module binding;
    Hit dragging;

    public ClickGui() { super(Text.literal("KSAWU23")); }

    @Override public boolean shouldPause() { return false; }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        hits.clear();
        int acc = Theme.color();
        int accSoft = (acc & 0x00FFFFFF) | 0xAA000000;
        if (Modules.theme.bgOn.value && Theme.bg != null)
            c.drawTexture(RenderLayer::getGuiTextured, Theme.bg, 0, 0, 0f, 0f, width, height, width, height);
        else c.fill(0, 0, width, height, 0x99000000);

        int pw = Math.min(112, (width - 20) / CATS.length - 6);
        int x0 = (width - (pw + 6) * CATS.length + 6) / 2;
        c.drawCenteredTextWithShadow(textRenderer, "KSAWU23 Client Ana", width / 2, 6, acc);

        for (int i = 0; i < CATS.length; i++) {
            int x = x0 + i * (pw + 6), y = 20;
            c.fill(x, y, x + pw, y + 14, acc);
            c.drawCenteredTextWithShadow(textRenderer, CATS[i], x + pw / 2, y + 3, 0xFFFFFFFF);
            y += 15;
            for (Module m : Modules.ALL) {
                if (!m.category.equals(CATS[i])) continue;
                c.fill(x, y, x + pw, y + 13, m.enabled && !m.alwaysOn ? accSoft : 0xCC101010);
                String label = binding == m ? "[nacisnij klawisz...]"
                        : m.name + (m.key >= 0 ? " [" + Modules.keyName(m.key) + "]" : "");
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, pw - 4), x + 3, y + 3, 0xFFFFFFFF);
                Runnable expand = () -> {
                    boolean e = !m.expanded;
                    for (Module o : Modules.ALL) if (o.category.equals(m.category)) o.expanded = false;
                    m.expanded = e;
                };
                hits.add(new Hit(x, y, pw, 13, m.alwaysOn ? expand : m::toggle, expand, () -> binding = m, null));
                y += 14;
                if (m.expanded) for (Setting<?> s : m.settings) y = drawSetting(c, s, x, y, pw, acc);
            }
        }
        c.drawCenteredTextWithShadow(textRenderer,
                "LPM: wl/wyl | PPM: ustawienia | SPM: keybind | Prawy Ctrl: zamknij", width / 2, height - 12, 0xFFAAAAAA);
    }

    int drawSetting(DrawContext c, Setting<?> s, int x, int y, int pw, int acc) {
        int soft = (acc & 0x00FFFFFF) | 0x99000000;
        if (s instanceof Setting.Bool b) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.drawTextWithShadow(textRenderer, b.name, x + 5, y + 2, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, b.value ? "ON" : "OFF", x + pw - 22, y + 2, b.value ? 0xFF55FF55 : 0xFFFF5555);
            hits.add(new Hit(x + 2, y, pw - 2, 12, () -> b.value = !b.value, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Num n) {
            double f = (n.value - n.min) / (n.max - n.min);
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.fill(x + 2, y, x + 2 + (int) ((pw - 2) * f), y + 12, soft);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(String.format("%s: %.2f", n.name, n.value), pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            final int sx = x + 2, sw = pw - 2;
            Consumer<Double> d = mxv -> {
                double fr = Math.max(0, Math.min(1, (mxv - sx) / sw));
                double v = n.min + fr * (n.max - n.min);
                n.value = Math.max(n.min, Math.min(n.max, Math.round(v / n.step) * n.step));
            };
            hits.add(new Hit(sx, y, sw, 12, null, null, null, d));
            return y + 13;
        }
        if (s instanceof Setting.Mode md) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(md.name + ": " + md.value, pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            hits.add(new Hit(x + 2, y, pw - 2, 12, md::cycle, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Blocks bl) {
            c.drawTextWithShadow(textRenderer, bl.name + ":", x + 5, y + 2, acc);
            y += 12;
            for (String o : bl.options) {
                boolean on = bl.value.contains(o);
                c.fill(x + 6, y, x + pw, y + 11, on ? soft : 0xCC202020);
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(o, pw - 12), x + 9, y + 2, on ? 0xFFFFFFFF : 0xFF999999);
                hits.add(new Hit(x + 6, y, pw - 6, 11, () -> bl.toggle(o), null, null, null));
                y += 12;
            }
            return y;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Hit h : hits) {
            if (!h.in(mx, my)) continue;
            if (h.drag != null) { dragging = h; h.drag.accept(mx); return true; }
            Runnable r = button == 0 ? h.l : button == 1 ? h.r : button == 2 ? h.m : null;
            if (r != null) { r.run(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { dragging.drag.accept(mx); return true; }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) { dragging = null; return super.mouseReleased(mx, my, button); }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (binding != null) {
            binding.key = keyCode == GLFW.GLFW_KEY_ESCAPE ? -1 : keyCode;
            binding = null;
            return true;
        }
        if (Client.openKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public void removed() { Config.save(); }
}
