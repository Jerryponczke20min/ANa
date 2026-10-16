package pl.ksawu23.ana;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import org.lwjgl.glfw.GLFW;

public class Client implements ClientModInitializer {
    public static KeyBinding openKey;

    @Override
    public void onInitializeClient() {
        Modules.init();
        Config.load();

        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.ksawu23.gui", GLFW.GLFW_KEY_RIGHT_CONTROL, "category.ksawu23"));

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> Theme.load());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> Config.save());

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed()) mc.setScreen(new ClickGui());
            if (mc.player == null || mc.world == null) return;
            for (Module m : Modules.ALL) {
                if (m.key >= 0 && mc.currentScreen == null) {
                    boolean down = InputUtil.isKeyPressed(mc.getWindow().getHandle(), m.key);
                    if (down && !m.wasDown) m.toggle();
                    m.wasDown = down;
                }
                if (m.enabled) m.onTick();
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            for (Module m : Modules.ALL) if (m.enabled) m.onRender(ctx);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            for (Module m : Modules.ALL) if (m.enabled) m.onHud(ctx);
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient) {
                if (Modules.hitEffect.enabled) Modules.hitEffect.spawn(entity);
                if (Modules.killEffect.enabled) Modules.killEffect.target = entity;
            }
            return ActionResult.PASS;
        });

        // wlasne tlo ekwipunku (rysowane pod ekwipunkiem)
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof HandledScreen<?>) {
                ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, dt) -> {
                    if (Theme.inv != null && Modules.theme.invOn.value)
                        ctx.drawTexture(RenderLayer::getGuiTextured, Theme.inv, 0, 0, 0f, 0f, s.width, s.height, s.width, s.height);
                });
            }
        });
    }
}
