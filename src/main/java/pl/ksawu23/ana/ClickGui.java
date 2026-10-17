package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import java.io.File;
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
    float openT, sc = 1f;
    int lw, lh;
    static final float[][] P = new float[70][5];
    static boolean pInit;

    public ClickGui() { super(Text.literal("KSAWU23")); }

    @Override public boolean shouldPause() { return false; }

    static int lerp(int a, int b, float t) {
        int r = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            r |= (((int) (ca + (cb - ca) * t)) & 255) << s;
        }
        return r;
    }

    static int mul(int c, float f) {
        int r = (int) (((c >> 16) & 255) * f), g = (int) (((c >> 8) & 255) * f), b = (int) ((c & 255) * f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    int settingsHeight(Module m) {
        int h = 0;
        for (Setting<?> s : m.settings) h += s instanceof Setting.Blocks b ? 12 + b.options.size() * 12 : 13;
        return h;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        hits.clear();
        var th = Modules.theme;
        sc = th.size.value.floatValue();
        lw = (int) (width / sc);
        lh = (int) (height / sc);
        double mxl = mx / sc, myl = my / sc;
        boolean anim = th.anims.value;
        openT = anim ? Math.min(1f, openT + 0.08f) : 1f;
        float e = 1f - (float) Math.pow(1 - openT, 3);
        int acc = Theme.color();

        // tlo
        if (th.bgOn.value && Theme.bg != null)
            c.drawTexture(RenderLayer::getGuiTextured, Theme.bg, 0, 0, 0f, 0f, width, height, width, height);
        else c.fill(0, 0, width, height, ((int) (0x99 * e) << 24));

        // czasteczki w tle
        if (!pInit) {
            for (float[] p : P) {
                p[0] = (float) (Math.random() * width); p[1] = (float) (Math.random() * height);
                p[2] = 0.25f + (float) Math.random() * 0.6f; p[3] = 2 + (float) (Math.random() * 2); p[4] = (float) (Math.random() * 6.28);
            }
            pInit = true;
        }
        if (th.parts.value) {
            String st = th.partStyle.value;
            boolean fall = !st.equals("Kropki");
            for (float[] p : P) {
                p[1] += fall ? p[2] : -p[2];
                p[0] += (float) Math.sin(p[1] / 25.0 + p[4]) * (st.equals("Platki wisni") ? 0.7f : 0.3f);
                if (p[1] > height + 4) { p[1] = -4; p[0] = (float) (Math.random() * width); }
                if (p[1] < -4) { p[1] = height; p[0] = (float) (Math.random() * width); }
                int al = Math.max(8, (int) (170 * e));
                int col;
                if (st.equals("Platki wisni")) col = lerp(0xFFFFC2DA, 0xFFFF7FB0, (float) (Math.sin(p[4]) * 0.5 + 0.5));
                else if (st.equals("Snieg")) col = 0xFFFFFFFF;
                else if (st.equals("Tecza")) col = 0xFF000000 | (java.awt.Color.HSBtoRGB((float) ((p[4] / 6.28 + System.currentTimeMillis() / 6000.0) % 1.0), 0.6f, 1f) & 0xFFFFFF);
                else col = acc;
                int sz = (int) p[3];
                c.fill((int) p[0], (int) p[1], (int) p[0] + sz, (int) p[1] + sz, (al << 24) | (col & 0xFFFFFF));
            }
        }

        var mat = c.getMatrices();
        mat.push();
        mat.scale(sc, sc, 1f);
        mat.translate(0, (1f - e) * -25f, 0);

        mat.push();
        mat.translate(lw / 2f, 4, 0);
        mat.scale(1.5f, 1.5f, 1f);
        c.drawCenteredTextWithShadow(textRenderer, "KSAWU23 Client Ana", 0, 0, acc);
        mat.pop();

        int pw = Math.min(112, (lw - 20) / CATS.length - 6);
        int x0 = (lw - (pw + 6) * CATS.length + 6) / 2;
        int accSoft = (acc & 0x00FFFFFF) | 0xAA000000;

        for (int i = 0; i < CATS.length; i++) {
            int x = x0 + i * (pw + 6), y = 22;
            c.fillGradient(x, y, x + pw, y + 15, acc, mul(acc, 0.55f));
            c.drawBorder(x, y, pw, 15, mul(acc, 1.0f));
            c.drawCenteredTextWithShadow(textRenderer, CATS[i], x + pw / 2, y + 4, 0xFFFFFFFF);
            y += 17;

            for (Module m : Modules.ALL) {
                if (!m.category.equals(CATS[i])) continue;
                boolean on = m.enabled && !m.alwaysOn;
                float tt = on ? 1f : 0f;
                m.tog = anim ? m.tog + (tt - m.tog) * 0.25f : tt;
                boolean hover = mxl >= x && mxl < x + pw && myl >= y && myl < y + 13;
                int base = lerp(0xCC141418, accSoft, m.tog);
                if (hover) base = lerp(base, 0xFFFFFFFF, 0.12f);
                c.fill(x, y, x + pw, y + 13, base);
                if (m.tog > 0.05f) c.fill(x, y, x + 2, y + 13, ((int) (255 * m.tog) << 24) | (acc & 0xFFFFFF));
                String label = binding == m ? "[nacisnij klawisz...]"
                        : m.name + (m.key >= 0 ? " [" + Modules.keyName(m.key) + "]" : "");
                int tc = lerp(0xFFAAAAAA, 0xFFFFFFFF, Math.max(m.tog, hover ? 1f : 0f));
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(label, pw - 14), x + 4, y + 3, tc);
                if (!m.settings.isEmpty())
                    c.drawTextWithShadow(textRenderer, m.expanded ? "-" : "+", x + pw - 9, y + 3, 0xFFAAAAAA);

                Runnable expand = () -> {
                    boolean ex = !m.expanded;
                    for (Module o : Modules.ALL) if (o.category.equals(m.category)) o.expanded = false;
                    m.expanded = ex;
                };
                hits.add(new Hit(x, y, pw, 13, m.alwaysOn ? expand : m::toggle, expand, () -> binding = m, null));
                y += 14;

                float target = m.expanded ? 1f : 0f;
                m.anim = anim ? m.anim + (target - m.anim) * 0.3f : target;
                if (Math.abs(target - m.anim) < 0.01f) m.anim = target;
                int sh = settingsHeight(m);
                int vh = (int) (sh * m.anim);
                if (vh > 0) {
                    boolean live = m.anim > 0.97f;
                    c.enableScissor(x, y, x + pw, y + vh);
                    int yy = y;
                    for (Setting<?> s : m.settings) yy = drawSetting(c, s, x, yy, pw, acc, live);
                    c.disableScissor();
                    y += vh;
                }
            }
        }

        String path = new File(MinecraftClient.getInstance().runDirectory, "ksawu23").getAbsolutePath();
        c.drawCenteredTextWithShadow(textRenderer, "Obrazki (gui.png, inventory.png) wrzucaj do: " + path, lw / 2, lh - 24, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(textRenderer,
                "LPM: wl/wyl | PPM: ustawienia | SPM: keybind | Prawy Ctrl: zamknij", lw / 2, lh - 12, 0xFF888888);
        mat.pop();
    }

    int drawSetting(DrawContext c, Setting<?> s, int x, int y, int pw, int acc, boolean live) {
        int soft = (acc & 0x00FFFFFF) | 0x99000000;
        if (s instanceof Setting.Bool b) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(b.name, pw - 30), x + 5, y + 2, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, b.value ? "ON" : "OFF", x + pw - 22, y + 2, b.value ? 0xFF55FF55 : 0xFFFF5555);
            if (live) hits.add(new Hit(x + 2, y, pw - 2, 12, () -> b.value = !b.value, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Num n) {
            double f = (n.value - n.min) / (n.max - n.min);
            int fw = (int) ((pw - 2) * f);
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.fillGradient(x + 2, y, x + 2 + fw, y + 12, soft, mul(acc, 0.4f) & 0x00FFFFFF | 0x99000000);
            c.fill(x + 1 + fw, y, x + 3 + fw, y + 12, 0xFFFFFFFF);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(String.format("%s: %.2f", n.name, n.value), pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            final int sx = x + 2, sw = pw - 2;
            Consumer<Double> d = mxv -> {
                double fr = Math.max(0, Math.min(1, (mxv - sx) / sw));
                double v = n.min + fr * (n.max - n.min);
                n.value = Math.max(n.min, Math.min(n.max, Math.round(v / n.step) * n.step));
            };
            if (live) hits.add(new Hit(sx, y, sw, 12, null, null, null, d));
            return y + 13;
        }
        if (s instanceof Setting.Mode md) {
            c.fill(x + 2, y, x + pw, y + 12, 0xCC202020);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(md.name + ": " + md.value, pw - 6), x + 5, y + 2, 0xFFFFFFFF);
            if (live) hits.add(new Hit(x + 2, y, pw - 2, 12, md::cycle, null, null, null));
            return y + 13;
        }
        if (s instanceof Setting.Blocks bl) {
            c.drawTextWithShadow(textRenderer, bl.name + ":", x + 5, y + 2, acc);
            y += 12;
            for (String o : bl.options) {
                boolean on = bl.value.contains(o);
                c.fill(x + 6, y, x + pw, y + 11, on ? soft : 0xCC202020);
                c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(o, pw - 12), x + 9, y + 2, on ? 0xFFFFFFFF : 0xFF999999);
                if (live) hits.add(new Hit(x + 6, y, pw - 6, 11, () -> bl.toggle(o), null, null, null));
                y += 12;
            }
            return y;
        }
        return y;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double lx = mx / sc, ly = my / sc;
        for (Hit h : hits) {
            if (!h.in(lx, ly)) continue;
            if (h.drag != null) { dragging = h; h.drag.accept(lx); return true; }
            Runnable r = button == 0 ? h.l : button == 1 ? h.r : button == 2 ? h.m : null;
            if (r != null) { r.run(); return true; }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging != null) { dragging.drag.accept(mx / sc); return true; }
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
