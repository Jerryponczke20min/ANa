package pl.ksawu23.ana;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public final class Extra {

    static PlayerEntity nearest(double range, java.util.function.Predicate<PlayerEntity> f) {
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        PlayerEntity best = null; double bd = range * range;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (p == mc.player || !p.isAlive() || !f.test(p)) continue;
            double d = p.squaredDistanceTo(mc.player);
            if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    /** Wedka nielota: gdy gracz leci na elytrze, wyciaga wedke, celuje i rzuca/zwija. */
    public static class AntiElytraRod extends Module {
        final Setting.Num range = add(new Setting.Num("Zasieg", 5, 40, 1, 20));
        final Setting.Num delay = add(new Setting.Num("Opoznienie (tick)", 5, 40, 1, 15));
        final Setting.Num lead = add(new Setting.Num("Predykcja ruchu", 0, 10, 0.5, 3));
        final Setting.Bool aim = add(new Setting.Bool("Celuj w gracza", true));
        final Setting.Bool back = add(new Setting.Bool("Wroc do slotu", true));
        int cd, prev = -1;

        AntiElytraRod() { super("Anti Elytra Rod", "Combat"); }

        public void onTick() {
            PlayerEntity t = nearest(range.value, PlayerEntity::isGliding);
            int rod = Util.findHotbar(s -> s.isOf(Items.FISHING_ROD));
            if (t == null || rod < 0) {
                if (prev >= 0 && back.value) { Util.select(prev); }
                prev = -1; cd = 0;
                return;
            }
            if (prev < 0) prev = mc.player.getInventory().selectedSlot;
            Util.select(rod);
            if (aim.value) {
                Vec3d pos = t.getPos().add(t.getVelocity().multiply(lead.value)).add(0, t.getHeight() / 2, 0);
                Vec3d d = pos.subtract(mc.player.getEyePos());
                mc.player.setYaw((float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90));
                mc.player.setPitch((float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
            }
            if (cd-- > 0) return;
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);   // rzut, potem zwiniecie
            mc.player.swingHand(Hand.MAIN_HAND);
            cd = delay.value.intValue();
        }

        public void onDisable() { if (prev >= 0 && mc.player != null) Util.select(prev); prev = -1; }
    }

    /** Bicie przez sciany: atakuje gracza na linii celowania (z rozszerzonym hitboxem), ignorujac bloki. */
    public static class WallHit extends Module {
        final Setting.Num reach = add(new Setting.Num("Zasieg", 2, 6, 0.1, 3.0));
        final Setting.Num extra = add(new Setting.Num("Dodatkowy hitbox", 0, 1, 0.05, 0.1));
        final Setting.Bool onlyClick = add(new Setting.Bool("Tylko po kliknieciu", true));
        final Setting.Bool cooldown = add(new Setting.Bool("Czekaj na cooldown", true));

        WallHit() { super("Wall Hit", "Combat"); }

        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            if (onlyClick.value && !mc.options.attackKey.isPressed()) return;
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) return; // vanilla bije
            if (cooldown.value && mc.player.getAttackCooldownProgress(0.5f) < 0.9f) return;
            Vec3d eye = mc.player.getEyePos();
            Vec3d end = eye.add(mc.player.getRotationVec(1f).multiply(reach.value));
            Entity best = null; double bd = Double.MAX_VALUE;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == mc.player || !p.isAlive()) continue;
                Box b = p.getBoundingBox().expand(p.getTargetingMargin() + extra.value);
                Optional<Vec3d> hit = b.raycast(eye, end);
                if (hit.isPresent()) {
                    double d = eye.squaredDistanceTo(hit.get());
                    if (d < bd) { bd = d; best = p; }
                }
            }
            if (best != null) {
                mc.interactionManager.attackEntity(mc.player, best);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }

    /** Podswietlenie graczy przez sciany (client-side glow). */
    public static class GlowEsp extends Module {
        GlowEsp() { super("Glow ESP", "Visuals"); }
        public void onTick() {
            for (PlayerEntity p : mc.world.getPlayers()) if (p != mc.player) p.setGlowing(true);
        }
        public void onDisable() {
            if (mc.world != null) for (PlayerEntity p : mc.world.getPlayers()) p.setGlowing(false);
        }
    }

    /** Lista graczy w poblizu: HP, elytra, itemy w rekach i zbroja (z wytrzymaloscia). */
    public static class PlayerInfo extends Module {
        final Setting.Num x = add(new Setting.Num("X", 0, 900, 1, 4));
        final Setting.Num y = add(new Setting.Num("Y", 0, 500, 1, 120));
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.5, 2, 0.1, 1.0));
        final Setting.Num range = add(new Setting.Num("Zasieg", 8, 128, 4, 48));
        final Setting.Num max = add(new Setting.Num("Max graczy", 1, 10, 1, 6));
        final Setting.Bool items = add(new Setting.Bool("Pokaz itemy", true));

        PlayerInfo() { super("Player Info", "HUD"); }

        public void onHud(DrawContext d) {
            List<PlayerEntity> list = new ArrayList<>();
            for (PlayerEntity p : mc.world.getPlayers())
                if (p != mc.player && p.squaredDistanceTo(mc.player) <= range.value * range.value) list.add(p);
            list.sort(Comparator.comparingDouble(p -> p.squaredDistanceTo(mc.player)));
            var m = d.getMatrices();
            m.push();
            m.translate(x.value, y.value, 0);
            m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
            int yy = 0, n = 0;
            for (PlayerEntity p : list) {
                if (n++ >= max.value) break;
                float hp = p.getHealth(), ab = p.getAbsorptionAmount();
                int col = hp > 14 ? 0xFF55FF55 : hp > 7 ? 0xFFFFFF55 : 0xFFFF5555;
                String hpTxt = String.format("%.1f%s HP", hp, ab > 0 ? " +" + String.format("%.0f", ab) : "");
                d.drawTextWithShadow(mc.textRenderer, p.getName().getString() + " " + (int) p.distanceTo(mc.player) + "m", 0, yy, 0xFFFFFFFF);
                d.drawTextWithShadow(mc.textRenderer, hpTxt, 0, yy + 10, col);
                if (p.isGliding()) d.drawTextWithShadow(mc.textRenderer, "[ELYTRA]", 60, yy + 10, 0xFFFF5555);
                yy += 21;
                if (items.value) {
                    ItemStack[] st = {p.getMainHandStack(), p.getOffHandStack(),
                            p.getEquippedStack(EquipmentSlot.HEAD), p.getEquippedStack(EquipmentSlot.CHEST),
                            p.getEquippedStack(EquipmentSlot.LEGS), p.getEquippedStack(EquipmentSlot.FEET)};
                    for (int i = 0; i < st.length; i++) {
                        d.drawItem(st[i], i * 18, yy);
                        d.drawStackOverlay(mc.textRenderer, st[i], i * 18, yy);
                    }
                    yy += 19;
                }
                yy += 3;
            }
            m.pop();
        }
    }
}
