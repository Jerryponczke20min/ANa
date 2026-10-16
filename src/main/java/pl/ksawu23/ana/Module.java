package pl.ksawu23.ana;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import java.util.*;

public abstract class Module {
    public final String name, category;
    public boolean enabled, expanded, wasDown, alwaysOn;
    public int key = -1;
    public final List<Setting<?>> settings = new ArrayList<>();
    protected final MinecraftClient mc = MinecraftClient.getInstance();

    protected Module(String name, String category) { this.name = name; this.category = category; }

    protected <S extends Setting<?>> S add(S s) { settings.add(s); return s; }

    public void toggle() {
        if (alwaysOn) return;
        enabled = !enabled;
        if (enabled) onEnable(); else onDisable();
    }

    public void onEnable() {}
    public void onDisable() {}
    public void onTick() {}
    public void onRender(WorldRenderContext c) {}
    public void onHud(DrawContext d) {}
}
