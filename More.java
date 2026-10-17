package pl.ksawu23.ana;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.Vec3d;

/** Pusty input - postac stoi w miejscu, gdy kamera Freecam lata sama. */
class FrozenInput extends Input {}

public final class More {

    /** Kierunek ruchu wg klawiszy (znormalizowany X/Z) albo null, gdy nic nie wcisniete. */
    static double[] dir() {
        var mc = MinecraftClient.getInstance();
        var o = mc.options;
        double f = (o.forwardKey.isPressed() ? 1 : 0) - (o.backKey.isPressed() ? 1 : 0);
        double s = (o.leftKey.isPressed() ? 1 : 0) - (o.rightKey.isPressed() ? 1 : 0);
        if (f == 0 && s == 0) return null;
        double rad = Math.toRadians(mc.player.getYaw());
        double vx = -Math.sin(rad) * f + Math.cos(rad) * s, vz = Math.cos(rad) * f + Math.sin(rad) * s;
        double len = Math.sqrt(vx * vx + vz * vz);
        return new double[]{vx / len, vz / len};
    }

    public static class Freecam extends Module {
        final Setting.Num speed = add(new Setting.Num("Predkosc (bl/s)", 4, 100, 1, 20));
        final Setting.Bool blockClicks = add(new Setting.Bool("Blokuj klikanie", true));
        OtherClientPlayerEntity cam;
        Input oldInput;
        double cx, cy, cz;
        long last;

        Freecam() { super("Freecam", "Visuals"); }

        public boolean blocks() { return enabled && blockClicks.value; }

        void start() {
            if (cam != null || mc.player == null || mc.world == null) return;
            cam = new OtherClientPlayerEntity(mc.world, mc.player.getGameProfile());
            cam.copyPositionAndRotation(mc.player);
            cx = mc.player.getX(); cy = mc.player.getY(); cz = mc.player.getZ();
            oldInput = mc.player.input;
            mc.player.input = new FrozenInput();
            mc.setCameraEntity(cam);
            last = System.nanoTime();
        }

        void stop() {
            if (mc.player != null) {
                if (oldInput != null) mc.player.input = oldInput;
                mc.setCameraEntity(mc.player);
            }
            cam = null; oldInput = null;
        }

        public void onEnable() { start(); }
        public void onDisable() { stop(); }

        public void onTick() {
            if (cam == null) start();
            else if (mc.player == null || cam.getWorld() != mc.world) stop();
        }

        // ruch kamery liczony co klatke - dzieki temu jest plynny
        public void onHud(DrawContext d) {
            if (cam == null || mc.player == null) return;
            long now = System.nanoTime();
            double dt = Math.min(0.1, (now - last) / 1e9);
            last = now;
            float yaw = mc.player.getYaw(), pitch = mc.player.getPitch();
            Vec3d fwd = Vec3d.fromPolar(pitch, yaw);
            double rad = Math.toRadians(yaw);
            Vec3d right = new Vec3d(-Math.cos(rad), 0, -Math.sin(rad));
            var o = mc.options;
            Vec3d mv = Vec3d.ZERO;
            if (o.forwardKey.isPressed()) mv = mv.add(fwd);
            if (o.backKey.isPressed()) mv = mv.subtract(fwd);
            if (o.rightKey.isPressed()) mv = mv.add(right);
            if (o.leftKey.isPressed()) mv = mv.subtract(right);
            if (o.jumpKey.isPressed()) mv = mv.add(0, 1, 0);
            if (o.sneakKey.isPressed()) mv = mv.add(0, -1, 0);
            if (mv.lengthSquared() > 0) {
                mv = mv.normalize().multiply(speed.value * dt);
                cx += mv.x; cy += mv.y; cz += mv.z;
            }
            cam.setPosition(cx, cy, cz);
            cam.prevX = cx; cam.prevY = cy; cam.prevZ = cz;
            cam.lastRenderX = cx; cam.lastRenderY = cy; cam.lastRenderZ = cz;
            cam.setYaw(yaw); cam.prevYaw = yaw;
            cam.setPitch(pitch); cam.prevPitch = pitch;
        }
    }

    public static class Zoom extends Module {
        final Setting.Num fov = add(new Setting.Num("FOV", 30, 110, 1, 30));
        Integer orig;

        Zoom() { super("Zoom", "Visuals"); }

        public void onTick() {
            if (orig == null) orig = mc.options.getFov().getValue();
            mc.options.getFov().setValue(fov.value.intValue());
        }

        public void onDisable() {
            if (orig != null && mc.options != null) mc.options.getFov().setValue(orig);
            orig = null;
        }
    }

    /** Zaklada zbroje z ekwipunku w puste sloty (shift-klik). */
    public static class AutoArmor extends Module {
        int t;
        AutoArmor() { super("Auto Armor", "Anarchia"); }

        public void onTick() {
            if (mc.currentScreen != null || t++ % 10 != 0) return;
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (EquipmentSlot es : slots) {
                if (!mc.player.getEquippedStack(es).isEmpty()) continue;
                for (int i = 0; i < 36; i++) {
                    ItemStack st = mc.player.getInventory().getStack(i);
                    if (st.isEmpty() || mc.player.getPreferredEquipmentSlot(st) != es) continue;
                    mc.interactionManager.clickSlot(0, i < 9 ? 36 + i : i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    return;
                }
            }
        }
    }

    /** Je jedzenie z hotbara, gdy glod spadnie ponizej progu. */
    public static class AutoEat extends Module {
        final Setting.Num missing = add(new Setting.Num("Jedz gdy brakuje (drumstickow)", 0.5, 10, 0.5, 1));
        final Setting.Bool full = add(new Setting.Bool("Jedz do pelna", false));
        boolean eating;
        int prev = -1;

        AutoEat() { super("Auto Eat", "Anarchia"); }

        int thr() { return 20 - (int) Math.round(missing.value * 2); }

        void stopEating() {
            mc.options.useKey.setPressed(false);
            if (prev >= 0) Util.select(prev);
            prev = -1; eating = false;
        }

        public void onTick() {
            if (mc.currentScreen != null) return;
            int food = mc.player.getHungerManager().getFoodLevel();
            if (!eating) {
                if (food > thr()) return;
                int slot = Util.findHotbar(s -> s.contains(DataComponentTypes.FOOD));
                if (slot < 0) return;
                prev = mc.player.getInventory().selectedSlot;
                Util.select(slot);
                mc.options.useKey.setPressed(true);
                eating = true;
            } else if (((full.value ? food >= 20 : food > thr()) && !mc.player.isUsingItem())
                    || !mc.player.getMainHandStack().contains(DataComponentTypes.FOOD)) {
                stopEating();
            }
        }

        public void onDisable() { if (eating) stopEating(); }
    }

    public static class Speed extends Module {
        final Setting.Num mult = add(new Setting.Num("Mnoznik", 1, 3, 0.05, 1.5));
        final Setting.Bool ground = add(new Setting.Bool("Tylko na ziemi", false));
        Speed() { super("Speed", "Anarchia"); }

        public void onTick() {
            if (ground.value && !mc.player.isOnGround()) return;
            double[] d = dir();
            if (d == null) return;
            double sp = 0.2873 * mult.value;
            Vec3d v = mc.player.getVelocity();
            mc.player.setVelocity(d[0] * sp, v.y, d[1] * sp);
        }
    }

    public static class Fly extends Module {
        final Setting.Num speed = add(new Setting.Num("Predkosc", 0.1, 3, 0.1, 0.6));
        int t;
        Fly() { super("Fly", "Anarchia"); }

        public void onTick() {
            var o = mc.options;
            double y = 0;
            if (o.jumpKey.isPressed()) y = speed.value * 0.6;
            else if (o.sneakKey.isPressed()) y = -speed.value * 0.6;
            else if (t++ % 20 == 0) y = -0.04;   // zapobiega kickowi "flying is not enabled"
            double[] d = dir();
            double hx = 0, hz = 0;
            if (d != null) { hx = d[0] * speed.value; hz = d[1] * speed.value; }
            mc.player.setVelocity(hx, y, hz);
            mc.player.fallDistance = 0;
        }
    }
}
