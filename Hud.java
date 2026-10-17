package pl.ksawu23.ana;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.Vec3d;
import java.util.*;

public final class Hud {

    /** Nametag jako panel nad graczem: nick, HP, nazwa miecza, druga reka, zbroja i elytra (ikona). */
    public static class Nametags extends Module {
        final Setting.Num range = add(new Setting.Num("Zasieg", 16, 128, 8, 64));
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.5, 2, 0.1, 1.0));
        final Setting.Bool self = add(new Setting.Bool("Pokaz u siebie", false));
        final Setting.Bool held = add(new Setting.Bool("Nazwa trzymanego itemu", true));
        final Setting.Bool items = add(new Setting.Bool("Ikony itemow i zbroi", true));

        Nametags() { super("Nametags", "Visuals"); }

        public void onHud(DrawContext d) {
            int sw = d.getScaledWindowWidth(), sh = d.getScaledWindowHeight();
            float td = mc.getRenderTickCounter().getTickDelta(false);
            Entity cam = mc.getCameraEntity();
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p == cam && !self.value) continue;
                if (p.distanceTo(mc.player) > range.value) continue;
                Vec3d pos = p.getLerpedPos(td).add(0, p.getHeight() + 0.6, 0);
                double[] s = Waypoints.project(pos, sw, sh);
                if (s == null) continue;

                float hp = p.getHealth() + p.getAbsorptionAmount();
                String name = p.getName().getString() + "  " + String.format("%.1f", hp);
                ItemStack main = p.getMainHandStack();
                String heldName = held.value && !main.isEmpty() ? main.getName().getString() : "";
                if (heldName.length() > 26) heldName = heldName.substring(0, 26);
                List<ItemStack> icons = new ArrayList<>();
                if (items.value) {
                    icons.add(main); icons.add(p.getOffHandStack());
                    icons.add(p.getEquippedStack(EquipmentSlot.HEAD)); icons.add(p.getEquippedStack(EquipmentSlot.CHEST));
                    icons.add(p.getEquippedStack(EquipmentSlot.LEGS)); icons.add(p.getEquippedStack(EquipmentSlot.FEET));
                    icons.removeIf(ItemStack::isEmpty);
                }
                int w = Math.max(mc.textRenderer.getWidth(name), Math.max(mc.textRenderer.getWidth(heldName), icons.size() * 18)) + 10;
                int h = 14 + (heldName.isEmpty() ? 0 : 10) + (icons.isEmpty() ? 0 : 19);

                var m = d.getMatrices();
                m.push();
                m.translate((float) s[0], (float) s[1], 0);
                m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
                int x0 = -w / 2, y0 = -h;
                int hpCol = hp > 14 ? 0xFF55FF55 : hp > 7 ? 0xFFFFFF55 : 0xFFFF5555;
                d.fill(x0, y0, x0 + w, y0 + h, 0xB0101018);
                d.drawBorder(x0, y0, w, h, hpCol);
                d.drawCenteredTextWithShadow(mc.textRenderer, name, 0, y0 + 3, hpCol);
                int yy = y0 + 13;
                if (!heldName.isEmpty()) { d.drawCenteredTextWithShadow(mc.textRenderer, heldName, 0, yy, 0xFFCCCCCC); yy += 10; }
                int ix = -icons.size() * 18 / 2 + 1;
                for (ItemStack st : icons) {
                    d.drawItem(st, ix, yy);
                    d.drawStackOverlay(mc.textRenderer, st, ix, yy);
                    if (p.isGliding() && st.isOf(net.minecraft.item.Items.ELYTRA)) d.drawBorder(ix - 1, yy - 1, 18, 18, 0xFFFF5555);
                    ix += 18;
                }
                m.pop();
            }
        }
    }

    public static class PotionHud extends Module {
        final Setting.Num x = add(new Setting.Num("X", 0, 900, 1, 4));
        final Setting.Num y = add(new Setting.Num("Y", 0, 500, 1, 60));
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.6, 2.5, 0.1, 1.0));

        PotionHud() { super("Potion HUD", "HUD"); }

        public void onHud(DrawContext d) {
            var m = d.getMatrices();
            m.push();
            m.translate(x.value, y.value, 0);
            m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
            int yy = 0;
            for (StatusEffectInstance e : mc.player.getStatusEffects()) {
                String name = e.getEffectType().value().getName().getString();
                String lvl = e.getAmplifier() > 0 ? " " + (e.getAmplifier() + 1) : "";
                int sec = e.getDuration() / 20;
                String time = e.isInfinite() ? "inf" : (sec / 60) + ":" + String.format("%02d", sec % 60);
                int col = e.getEffectType().value().isBeneficial() ? 0xFF55FF55 : 0xFFFF5555;
                d.drawTextWithShadow(mc.textRenderer, name + lvl + "  " + time, 0, yy, col);
                yy += 10;
            }
            m.pop();
        }
    }

    public static class ArmorHud extends Module {
        final Setting.Num x = add(new Setting.Num("X", 0, 900, 1, 4));
        final Setting.Num y = add(new Setting.Num("Y", 0, 500, 1, 200));
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.6, 2.5, 0.1, 1.0));
        final Setting.Bool vertical = add(new Setting.Bool("Pionowo", true));
        final Setting.Bool percent = add(new Setting.Bool("Procenty wytrzymalosci", true));

        ArmorHud() { super("Armor HUD", "HUD"); }

        public void onHud(DrawContext d) {
            var m = d.getMatrices();
            m.push();
            m.translate(x.value, y.value, 0);
            m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
            ItemStack[] st = {mc.player.getEquippedStack(EquipmentSlot.HEAD), mc.player.getEquippedStack(EquipmentSlot.CHEST),
                    mc.player.getEquippedStack(EquipmentSlot.LEGS), mc.player.getEquippedStack(EquipmentSlot.FEET),
                    mc.player.getMainHandStack(), mc.player.getOffHandStack()};
            int i = 0;
            for (ItemStack s : st) {
                if (s.isEmpty()) continue;
                int ix = vertical.value ? 0 : i * 40, iy = vertical.value ? i * 18 : 0;
                d.drawItem(s, ix, iy);
                d.drawStackOverlay(mc.textRenderer, s, ix, iy);
                if (percent.value && s.isDamageable()) {
                    int pc = 100 * (s.getMaxDamage() - s.getDamage()) / s.getMaxDamage();
                    d.drawTextWithShadow(mc.textRenderer, pc + "%", ix + 18, iy + 4, pc > 50 ? 0xFF55FF55 : pc > 20 ? 0xFFFFFF55 : 0xFFFF5555);
                }
                i++;
            }
            m.pop();
        }
    }

    /** Swiat: FPS, ping, wymiar, biom, predkosc i kierunek. */
    public static class InfoHud extends Module {
        final Setting.Num x = add(new Setting.Num("X", 0, 900, 1, 4));
        final Setting.Num y = add(new Setting.Num("Y", 0, 500, 1, 120));
        final Setting.Num scale = add(new Setting.Num("Rozmiar", 0.6, 2.5, 0.1, 1.0));
        double px, pz, bps;

        InfoHud() { super("Info HUD", "Swiat"); }

        public void onTick() {
            bps = Math.hypot(mc.player.getX() - px, mc.player.getZ() - pz) * 20;
            px = mc.player.getX(); pz = mc.player.getZ();
        }

        public void onHud(DrawContext d) {
            var pe = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
            String biome = mc.world.getBiome(mc.player.getBlockPos()).getKey().map(k -> k.getValue().getPath()).orElse("?");
            String[] lines = {
                    "FPS: " + mc.getCurrentFps(),
                    "Ping: " + (pe == null ? 0 : pe.getLatency()) + " ms",
                    "Wymiar: " + Waypoints.curDim(),
                    "Biom: " + biome,
                    "Predkosc: " + String.format("%.1f", bps) + " bl/s",
                    "Kierunek: " + mc.player.getHorizontalFacing().asString()};
            var m = d.getMatrices();
            m.push();
            m.translate(x.value, y.value, 0);
            m.scale(scale.value.floatValue(), scale.value.floatValue(), 1f);
            int yy = 0;
            for (String l : lines) { d.drawTextWithShadow(mc.textRenderer, l, 0, yy, Theme.color()); yy += 10; }
            m.pop();
        }
    }

    /** Anti Lag: lokalnie usuwa nadmiar encji (itemy, xp, TNT, spadajace bloki...) - najdalsze pierwsze. */
    public static class AntiLag extends Module {
        final Setting.Blocks types = add(new Setting.Blocks("Typy encji",
                List.of("item", "experience_orb", "tnt", "falling_block", "armor_stand", "minecart", "item_frame", "painting"),
                Set.of("item", "experience_orb", "tnt", "falling_block")));
        final Setting.Num max = add(new Setting.Num("Max na typ", 10, 200, 5, 40));
        int t;

        AntiLag() { super("Anti Lag", "Swiat"); }

        public void onTick() {
            if (t++ % 20 != 0) return;
            Map<String, List<Entity>> m = new HashMap<>();
            for (Entity e : mc.world.getEntities()) {
                String path = Registries.ENTITY_TYPE.getId(e.getType()).getPath();
                if (path.endsWith("minecart")) path = "minecart";
                if (!types.value.contains(path)) continue;
                m.computeIfAbsent(path, k -> new ArrayList<>()).add(e);
            }
            int lim = max.value.intValue();
            for (List<Entity> l : m.values()) {
                if (l.size() <= lim) continue;
                l.sort(Comparator.comparingDouble(e -> e.squaredDistanceTo(mc.player)));
                for (int i = lim; i < l.size(); i++) mc.world.removeEntity(l.get(i).getId(), Entity.RemovalReason.DISCARDED);
            }
        }
    }
}
