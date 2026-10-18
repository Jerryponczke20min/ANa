package pl.ksawu23.ana;

import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import java.util.*;

public final class Pvp {

    /** Crity "z powietrza": pakiety imitujace maly skok tuz przed uderzeniem. Dziala tez w pajeczynie. */
    public static class Criticals extends Module {
        final Setting.Mode mode = add(new Setting.Mode("Tryb", List.of("Maly", "Duzy"), "Maly"));
        final Setting.Bool web = add(new Setting.Bool("W pajeczynie", true));
        final Setting.Bool water = add(new Setting.Bool("W wodzie (serwer czesto blokuje)", true));

        Criticals() { super("Criticals", "PVP"); }

        void send(double x, double y, double z) {
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y, z, false, false));
        }

        /** Wywolywane tuz przed wyslaniem ataku. */
        public void doCrit() {
            var p = mc.player;
            if (p == null || mc.getNetworkHandler() == null || mc.world == null) return;
            boolean inWeb = mc.world.getBlockState(p.getBlockPos()).isOf(Blocks.COBWEB)
                    || mc.world.getBlockState(p.getBlockPos().up()).isOf(Blocks.COBWEB);
            boolean wet = p.isTouchingWater();
            if (!p.isOnGround() && !inWeb && !wet) return;       // w locie vanilla i tak daje crity
            if (inWeb && !web.value) return;
            if (wet && !water.value) return;
            double x = p.getX(), y = p.getY(), z = p.getZ();
            if (mode.value.equals("Duzy")) {
                send(x, y + 0.11, z); send(x, y + 0.1100013579, z); send(x, y + 0.0000013579, z);
            } else {
                send(x, y + 0.0625, z); send(x, y, z);
            }
        }
    }

    /** Gdy ktos blokuje tarcza: szybko bierze siekiere, uderza i wraca na poprzedni slot. */
    public static class ShieldBreaker extends Module {
        final Setting.Num reach = add(new Setting.Num("Zasieg", 2, 4.5, 0.1, 4.2));
        final Setting.Bool crosshair = add(new Setting.Bool("Tylko gdy celownik na graczu", true));
        int cd, restore = -1;

        ShieldBreaker() { super("Shield Breaker", "PVP"); }

        public void onTick() {
            if (restore >= 0) { Util.select(restore); restore = -1; }
            if (cd-- > 0 || mc.currentScreen != null || mc.interactionManager == null) return;
            PlayerEntity t = null;
            if (crosshair.value) {
                if (mc.crosshairTarget instanceof EntityHitResult ehr && ehr.getEntity() instanceof PlayerEntity p) t = p;
            } else {
                double bd = Double.MAX_VALUE;
                for (PlayerEntity p : mc.world.getPlayers()) {
                    double d = mc.player.distanceTo(p);
                    if (p != mc.player && d < bd) { bd = d; t = p; }
                }
            }
            if (t == null || t == mc.player || !t.isBlocking() || mc.player.distanceTo(t) > reach.value) return;
            int axe = Util.findHotbar(s -> s.isIn(ItemTags.AXES));
            if (axe < 0) return;
            int cur = mc.player.getInventory().selectedSlot;
            if (!mc.player.getMainHandStack().isIn(ItemTags.AXES)) {
                Util.select(axe);
                restore = cur;
            }
            mc.interactionManager.attackEntity(mc.player, t);
            mc.player.swingHand(Hand.MAIN_HAND);
            cd = 10;
        }
    }

    public static class AutoClicker extends Module {
        final Setting.Blocks targets = add(new Setting.Blocks("Cele", Hacks.TARGETS, Set.of("Gracze", "Wrogie moby")));
        final Setting.Num cps = add(new Setting.Num("CPS", 1, 20, 1, 10));
        final Setting.Num jitter = add(new Setting.Num("Losowosc CPS", 0, 5, 0.5, 2));
        final Setting.Bool onTarget = add(new Setting.Bool("Tylko gdy celownik na celu", true));
        final Setting.Bool holdOnly = add(new Setting.Bool("Tylko przy trzymaniu LPM", false));
        final Setting.Bool waitCd = add(new Setting.Bool("Czekaj na cooldown", false));
        double acc;

        AutoClicker() { super("Auto Clicker", "PVP"); }

        public void onTick() {
            if (mc.currentScreen != null || mc.interactionManager == null) return;
            if (holdOnly.value && !mc.options.attackKey.isPressed()) { acc = 0; return; }
            Entity e = null;
            if (mc.crosshairTarget instanceof EntityHitResult ehr) e = ehr.getEntity();
            if (onTarget.value && !(e instanceof LivingEntity le && Hacks.valid(targets.value, le))) { acc = 0; return; }
            acc += (cps.value + (Math.random() - 0.5) * 2 * jitter.value) / 20.0;
            while (acc >= 1) {
                acc -= 1;
                if (waitCd.value && mc.player.getAttackCooldownProgress(0.5f) < 0.9f) break;
                if (e != null) mc.interactionManager.attackEntity(mc.player, e);
                mc.player.swingHand(Hand.MAIN_HAND);
            }
        }
    }
}
