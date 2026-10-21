package pl.ksawu23.ana;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.ActionResult;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Client implements ClientModInitializer {
    public static KeyBinding openKey, openKey2;
    static final Logger LOG = LoggerFactory.getLogger("ksawu23");
    static boolean greeted;

    static void fail(Module m, Throwable t) {
        m.enabled = false;
        LOG.error("KSAWU23: modul {} wylaczony po bledzie", m.name, t);
        var mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.player.sendMessage(Text.literal("[KSAWU23] " + m.name + " wylaczony po bledzie: " + t), false);
    }

    @Override
    public void onInitializeClient() {
        Modules.init();
        Config.load();
        Waypoints.load();

        openKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.ksawu23.gui", GLFW.GLFW_KEY_RIGHT_CONTROL, "category.ksawu23"));

        openKey2 = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.ksawu23.gui2", GLFW.GLFW_KEY_RIGHT_SHIFT, "category.ksawu23"));
        LOG.info("KSAWU23 Client Ana: zaladowano {} modulow", Modules.ALL.size());

        ClientLifecycleEvents.CLIENT_STARTED.register(mc -> Theme.load());
        ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> { Config.save(); Waypoints.save(); });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.wasPressed() || openKey2.wasPressed()) mc.setScreen(new ClickGui());
            if (mc.player == null || mc.world == null) return;
            if (!greeted) {
                greeted = true;
                mc.player.sendMessage(Text.literal("[KSAWU23] Client Ana v2.0 zaladowany. Prawy Ctrl lub Prawy Shift = GUI."), false);
            }
            for (Module m : Modules.ALL) {
                try {
                    if (m.key >= 0 && mc.currentScreen == null) {
                        boolean down = InputUtil.isKeyPressed(mc.getWindow().getHandle(), m.key);
                        if (down && !m.wasDown) m.toggle();
                        m.wasDown = down;
                    }
                    if (m.enabled) m.onTick();
                } catch (Throwable t) { fail(m, t); }
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(ctx -> {
            for (Module m : Modules.ALL) if (m.enabled) { try { m.onRender(ctx); } catch (Throwable t) { fail(m, t); } }
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            for (Module m : Modules.ALL) if (m.enabled) { try { m.onHud(ctx); } catch (Throwable t) { fail(m, t); } }
        });

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient && Modules.freecam.blocks()) return ActionResult.FAIL;
            if (world.isClient) {
                if (Modules.crit.enabled) Modules.crit.doCrit();
                if (Modules.hitEffect.enabled) Modules.hitEffect.spawn(entity);
                if (Modules.killEffect.enabled) Modules.killEffect.target = entity;
            }
            return ActionResult.PASS;
        });

        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                world.isClient && (Modules.freecam.blocks() || Modules.noInteract.blockEntity(entity)) ? ActionResult.FAIL : ActionResult.PASS);
        UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                world.isClient && (Modules.freecam.blocks() || Modules.noInteract.blockBlock(world.getBlockState(hit.getBlockPos()))) ? ActionResult.FAIL : ActionResult.PASS);

        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) ->
                world.isClient && Modules.freecam.blocks() ? ActionResult.FAIL : ActionResult.PASS);

        // Incognito: podmiana Twojego nicku w wiadomosciach na czacie
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            var inc = Modules.incognito;
            if (inc != null && inc.enabled && !overlay && inc.needs(message)) {
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(inc.swap(message));
                return false;
            }
            return true;
        });
        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, ts) -> {
            var inc = Modules.incognito;
            if (inc != null && inc.enabled && inc.needs(message)) {
                MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(inc.swap(message));
                return false;
            }
            return true;
        });

        // wlasny wyglad ekwipunku: podmieniamy teksture panelu przy kazdym otwarciu
        ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
            if (screen instanceof net.minecraft.client.gui.screen.ingame.InventoryScreen) {
                Theme.applyInventory();
                ScreenEvents.beforeRender(screen).register((s, ctx, mx, my, dt) -> Theme.tickInventory());
            }
        });
    }
}
