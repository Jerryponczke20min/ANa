package pl.ksawu23.ana;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.math.*;
import org.joml.Matrix4f;
import java.util.*;
import java.util.function.Predicate;

public final class Trap {
    public static final LinkedHashMap<String, Predicate<String>> BLOCKS = new LinkedHashMap<>();
    public static final LinkedHashMap<String, Predicate<String>> ENTITIES = new LinkedHashMap<>();

    static {
        BLOCKS.put("Obsidian", p -> p.equals("obsidian") || p.equals("crying_obsidian"));
        BLOCKS.put("Kamien", p -> Set.of("cobblestone", "stone", "deepslate", "cobbled_deepslate", "end_stone", "stone_bricks", "netherrack", "blackstone").contains(p));
        BLOCKS.put("Netherite/Bedrock", p -> p.equals("netherite_block") || p.equals("bedrock") || p.equals("ancient_debris"));
        BLOCKS.put("Pajeczyna", p -> p.equals("cobweb"));
        BLOCKS.put("Plotki", p -> p.endsWith("_fence"));
        BLOCKS.put("Furtki", p -> p.endsWith("_fence_gate"));
        BLOCKS.put("Murki", p -> p.endsWith("_wall"));
        BLOCKS.put("Banery", p -> p.endsWith("_banner"));
        BLOCKS.put("Sculk", p -> p.startsWith("sculk"));
        BLOCKS.put("Zapadnie", p -> p.endsWith("_trapdoor"));
        BLOCKS.put("Drzwi", p -> p.endsWith("_door"));
        BLOCKS.put("Kowadla", p -> p.contains("anvil"));
        BLOCKS.put("Skrzynie", p -> p.equals("chest") || p.equals("trapped_chest") || p.equals("ender_chest") || p.equals("barrel") || p.endsWith("shulker_box"));
        BLOCKS.put("Plyty/Przyciski", p -> p.endsWith("_pressure_plate") || p.endsWith("_button"));
        BLOCKS.put("Dripstone", p -> p.equals("pointed_dripstone"));
        BLOCKS.put("Dywany", p -> p.endsWith("_carpet"));
        BLOCKS.put("Lancuchy/Tripwire", p -> p.equals("chain") || p.startsWith("tripwire"));
        BLOCKS.put("Kaktus/Magma", p -> p.equals("cactus") || p.equals("sweet_berry_bush") || p.equals("magma_block"));
        BLOCKS.put("Lawa/Woda", p -> p.equals("lava") || p.equals("water") || p.equals("powder_snow"));
        BLOCKS.put("Schody/Slaby", p -> p.endsWith("_stairs") || p.endsWith("_slab"));
        BLOCKS.put("Tloki", p -> p.contains("piston"));
        BLOCKS.put("Tabliczki", p -> p.endsWith("_sign"));
        BLOCKS.put("Pochodnie/Dzwignie", p -> p.endsWith("torch") || p.equals("lever") || p.endsWith("lantern"));
        BLOCKS.put("Tory", p -> p.endsWith("rail"));
        BLOCKS.put("Stoly/Piece", p -> Set.of("crafting_table", "furnace", "blast_furnace", "smoker", "smithing_table",
                "cartography_table", "fletching_table", "loom", "grindstone", "stonecutter", "enchanting_table",
                "lectern", "composter", "cauldron", "hopper").contains(p));
        BLOCKS.put("Drabiny/Winorosla", p -> p.equals("ladder") || p.equals("vine") || p.equals("scaffolding") || p.endsWith("vines"));

        ENTITIES.put("Item frame", p -> p.equals("item_frame") || p.equals("glow_item_frame"));
        ENTITIES.put("Obraz", p -> p.equals("painting"));
        ENTITIES.put("Wagonik", p -> p.equals("minecart"));
        ENTITIES.put("Wagonik specjalny", p -> p.endsWith("_minecart"));
        ENTITIES.put("Lodka", p -> p.endsWith("boat") || p.endsWith("raft"));
        ENTITIES.put("End crystal", p -> p.equals("end_crystal"));
        ENTITIES.put("Armor stand", p -> p.equals("armor_stand"));
    }

    static String kind(Map<String, Predicate<String>> m, Set<String> on, String path) {
        for (var e : m.entrySet()) if (on.contains(e.getKey()) && e.getValue().test(path)) return e.getKey();
        return null;
    }

    static String f1(double d) { return String.format("%.1f", d); }

    // =====================================================================
    public static class Detect extends Module {
        record Hit(Box box, BlockPos pos, Entity entity, String kind, double dist) {}

        public static boolean trapped;
        public static final List<Hit> hits = new ArrayList<>();
        final Map<String, double[]> summary = new LinkedHashMap<>();
        double nearest;

        final Setting.Blocks blocks = add(new Setting.Blocks("Bloki", List.copyOf(BLOCKS.keySet()),
                Set.of("Obsidian", "Plotki", "Furtki", "Banery", "Sculk", "Pajeczyna", "Zapadnie", "Tabliczki")));
        final Setting.Blocks entities = add(new Setting.Blocks("Encje", List.copyOf(ENTITIES.keySet()),
                Set.of("Item frame", "Obraz", "Wagonik", "Wagonik specjalny")));
        final Setting.Num range = add(new Setting.Num("Odleglosc (kratki)", 1, 12, 1, 4));
        final Setting.Num min = add(new Setting.Num("Min. do trapa", 1, 40, 1, 5));
        final Setting.Bool outline = add(new Setting.Bool("Podswietl w swiecie", true));
        final Setting.Bool panelOn = add(new Setting.Bool("Panel GUI", true));
        final Setting.Num px = add(new Setting.Num("Panel X", 0, 1000, 1, 8));
        final Setting.Num py = add(new Setting.Num("Panel Y", 0, 600, 1, 40));
        final Setting.Num scale = add(new Setting.Num("Panel rozmiar", 0.6, 2.5, 0.1, 1.0));
        final Setting.Num opacity = add(new Setting.Num("Panel przezroczystosc", 0.2, 1.0, 0.05, 0.9));
        final Setting.Num cr = add(new Setting.Num("Kolor R", 0, 255, 1, 255));
        final Setting.Num cg = add(new Setting.Num("Kolor G", 0, 255, 1, 50));
        final Setting.Num cb = add(new Setting.Num("Kolor B", 0, 255, 1, 50));
        final Setting.Mode colorMode = add(new Setting.Mode("Styl koloru panelu", List.of("Wlasny", "Tecza", "Wisnia"), "Wlasny"));
        final Setting.Bool anim = add(new Setting.Bool("Animacja wjazdu", true));
        final Setting.Bool pulse = add(new Setting.Bool("Pulsowanie ramki", true));
        final Setting.Bool sparksOn = add(new Setting.Bool("Czasteczki", true));

        int t;
        float panel;
        final List<float[]> sparks = new ArrayList<>();

        Detect() { super("Trap Detect", "Anarchia"); }

        int rgb() {
            switch (colorMode.value) {
                case "Tecza": return java.awt.Color.HSBtoRGB((System.currentTimeMillis() % 5000) / 5000f, 0.7f, 1f) & 0xFFFFFF;
                case "Wisnia": return 0xFF8FC0;
                default: return (cr.value.intValue() << 16) | (cg.value.intValue() << 8) | cb.value.intValue();
            }
        }

        int sparkRgb(float hue, int base) {
            if (colorMode.value.equals("Tecza")) return java.awt.Color.HSBtoRGB(hue, 0.6f, 1f) & 0xFFFFFF;
            if (colorMode.value.equals("Wisnia")) return ClickGui.lerp(0xFFFFC2DA, 0xFFFF7FB0, hue) & 0xFFFFFF;
            return base;
        }

        public void onDisable() { hits.clear(); trapped = false; panel = 0; sparks.clear(); }

        public void onTick() {
            if (t++ % 8 != 0) return;
            hits.clear(); summary.clear();
            Vec3d pp = mc.player.getPos();
            int r = range.value.intValue();
            double r2 = r * r + 0.5;
            BlockPos c = mc.player.getBlockPos();
            BlockPos.Mutable p = new BlockPos.Mutable();
            for (int x = -r; x <= r; x++) for (int y = -r; y <= r; y++) for (int z = -r; z <= r; z++) {
                if (x * x + y * y + z * z > r2) continue;
                p.set(c.getX() + x, c.getY() + y, c.getZ() + z);
                BlockState st = mc.world.getBlockState(p);
                if (st.isAir()) continue;
                String k = kind(BLOCKS, blocks.value, Registries.BLOCK.getId(st.getBlock()).getPath());
                if (k == null || hits.size() >= 500) continue;
                hits.add(new Hit(new Box(p), p.toImmutable(), null, k, Vec3d.ofCenter(p).distanceTo(pp)));
            }
            for (Entity e : mc.world.getOtherEntities(mc.player, new Box(c).expand(r), x -> true)) {
                String k = kind(ENTITIES, entities.value, Registries.ENTITY_TYPE.getId(e.getType()).getPath());
                double d = e.getPos().distanceTo(pp);
                if (k == null || d > r || hits.size() >= 500) continue;
                hits.add(new Hit(e.getBoundingBox(), null, e, k, d));
            }
            trapped = hits.size() >= min.value;
            nearest = 1e9;
            for (Hit h : hits) {
                nearest = Math.min(nearest, h.dist);
                double[] a = summary.computeIfAbsent(h.kind, k -> new double[]{0, 1e9});
                a[0]++; a[1] = Math.min(a[1], h.dist);
            }
        }

        public void onRender(WorldRenderContext c) {
            if (!outline.value || !trapped || hits.isEmpty()) return;
            Matrix4f m = Hacks.matrix(c);
            if (m == null) return;
            Vec3d cam = c.camera().getPos();
            int col = 0xFF000000 | rgb();
            RenderUtil.draw(m, b -> {
                for (Hit h : hits) RenderUtil.box(b, m, h.box.offset(-cam.x, -cam.y, -cam.z), col);
            });
        }

        record L(String t, int c) {}

        public void onHud(DrawContext d) {
            if (!panelOn.value) return;
            float tgt = trapped ? 1f : 0f;
            panel += (tgt - panel) * 0.18f;
            if (Math.abs(tgt - panel) < 0.01f) panel = tgt;
            if (panel <= 0f) return;
            boolean an = anim.value;
            float e = an ? 1f - (float) Math.pow(1 - panel, 3) : 1f;
            int a = Math.max(8, (int) (255 * e * opacity.value));
            int rgb = rgb();

            List<L> lines = new ArrayList<>();
            lines.add(new L("Najblizszy: " + f1(nearest) + " kr.", 0xFFFFFF));
            lines.add(new L("Elementow: " + hits.size() + " (min " + min.value.intValue() + ")", 0xCCCCCC));
            int n = 0;
            for (var en : summary.entrySet()) {
                if (n++ >= 6) break;
                lines.add(new L(en.getKey() + " x" + (int) en.getValue()[0] + "  " + f1(en.getValue()[1]) + " kr.", rgb));
            }
            int w = 130;
            for (L l : lines) w = Math.max(w, mc.textRenderer.getWidth(l.t) + 14);
            int h = 20 + lines.size() * 11;
            float s = scale.value.floatValue();

            var m = d.getMatrices();
            m.push();
            float slide = an ? (1f - e) * -(w + 30) * s : 0f;
            m.translate(px.value + slide, py.value, 0);
            m.scale(s, s, 1f);

            float pul = pulse.value ? 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 180.0) : 1f;
            d.fill(0, 0, w, h, ((int) (a * 0.85f) << 24) | 0x0A0A12);
            d.fillGradient(0, 0, w, 15, (a << 24) | rgb, (Math.max(8, a / 4) << 24) | rgb);
            d.drawBorder(0, 0, w, h, ((int) (a * pul) << 24) | rgb);
            d.drawTextWithShadow(mc.textRenderer, "!! TRAP WYKRYTY !!", 5, 4, (a << 24) | 0xFFFFFF);

            int y = 19;
            for (L l : lines) {
                d.drawTextWithShadow(mc.textRenderer, l.t, 5, y, (a << 24) | l.c);
                y += 11;
            }

            boolean cherry = colorMode.value.equals("Wisnia");
            if (sparksOn.value && trapped && sparks.size() < 45 && Math.random() < 0.6)
                sparks.add(new float[]{(float) (Math.random() * w), cherry ? 0f : h, (float) ((0.3 + Math.random() * 0.8) * (cherry ? -0.6 : 1)), 1f, (float) Math.random()});
            Iterator<float[]> it = sparks.iterator();
            while (it.hasNext()) {
                float[] sp = it.next();
                sp[1] -= sp[2]; sp[3] -= 0.018f;
                if (cherry) sp[0] += (float) Math.sin(sp[1] / 5.0) * 0.5f;
                if (sp[3] <= 0f) { it.remove(); continue; }
                int al = Math.max(8, (int) (a * sp[3]));
                d.fill((int) sp[0], (int) sp[1], (int) sp[0] + 2, (int) sp[1] + 2, (al << 24) | sparkRgb(sp[4], rgb));
            }
            m.pop();
        }
    }

    // =====================================================================
    public static class Escape extends Module {
        final Setting.Blocks breakBlocks = add(new Setting.Blocks("Rozwalaj bloki", List.copyOf(BLOCKS.keySet()),
                Set.of("Obsidian", "Plotki", "Furtki", "Banery", "Sculk", "Pajeczyna", "Zapadnie", "Tabliczki")));
        final Setting.Blocks breakEnt = add(new Setting.Blocks("Rozwalaj encje", List.copyOf(ENTITIES.keySet()),
                Set.of("Item frame", "Obraz", "Wagonik", "Wagonik specjalny")));
        final Setting.Num radius = add(new Setting.Num("Zasieg szukania", 1, 4, 1, 2));
        final Setting.Bool only = add(new Setting.Bool("Tylko w trapie", true));
        final Setting.Bool floor = add(new Setting.Bool("Niszcz podloge", false));
        final Setting.Bool fullEq = add(new Setting.Bool("Narzedzie z calego EQ", true));
        final Setting.Bool placeAbove = add(new Setting.Bool("Stawiaj nad glowa", true));
        final Setting.Mode placeBlock = add(new Setting.Mode("Blok nad glowa",
                List.of("cobblestone", "obsidian", "stone", "end_stone", "netherrack", "cobbled_deepslate"), "cobblestone"));
        final Setting.Num delay = add(new Setting.Num("Opoznienie (tick)", 0, 10, 1, 0));
        int cd;

        Escape() { super("Trap Escape", "Anarchia"); }

        public void onTick() {
            if (mc.interactionManager == null) return;
            if (only.value && Modules.trapDetect.enabled && !Detect.trapped) return;
            if (cd-- > 0) return;
            BlockPos f = mc.player.getBlockPos();
            int r = radius.value.intValue();
            Vec3d pp = mc.player.getPos();
            BlockPos bestPos = null; Entity bestEnt = null; double bd = Double.MAX_VALUE; BlockState bst = null;

            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++)
                for (int dy = (floor.value ? -1 : 0); dy <= r + 1; dy++) {
                    BlockPos p = f.add(dx, dy, dz);
                    BlockState st = mc.world.getBlockState(p);
                    if (st.isAir() || !st.getFluidState().isEmpty()) continue;
                    if (kind(BLOCKS, breakBlocks.value, Registries.BLOCK.getId(st.getBlock()).getPath()) == null) continue;
                    if (!Util.inReach(p)) continue;
                    double d = Vec3d.ofCenter(p).squaredDistanceTo(pp);
                    if (d < bd) { bd = d; bestPos = p; bst = st; bestEnt = null; }
                }
            for (Entity e : mc.world.getOtherEntities(mc.player, mc.player.getBoundingBox().expand(4.5), x -> true)) {
                if (kind(ENTITIES, breakEnt.value, Registries.ENTITY_TYPE.getId(e.getType()).getPath()) == null) continue;
                if (mc.player.distanceTo(e) > 4.5) continue;
                double d = e.squaredDistanceTo(pp);
                if (d < bd) { bd = d; bestEnt = e; bestPos = null; }
            }

            if (bestEnt != null) {
                int w = Util.findHotbar(s -> s.isIn(ItemTags.SWORDS) || s.isIn(ItemTags.AXES));
                Util.select(w);
                mc.interactionManager.attackEntity(mc.player, bestEnt);
                mc.player.swingHand(Hand.MAIN_HAND);
                cd = delay.value.intValue();
                return;
            }
            if (bestPos != null) {
                if (fullEq.value) Util.equipBestTool(bst); else Util.select(Util.bestTool(bst));
                Util.breakBlock(bestPos);
                cd = delay.value.intValue();
                return;
            }
            if (placeAbove.value) {
                BlockPos above = f.up(2);
                if (mc.world.getBlockState(above).isAir()) {
                    int slot = Util.findHotbar(s -> s.getItem() instanceof net.minecraft.item.BlockItem bi
                            && Registries.BLOCK.getId(bi.getBlock()).getPath().equals(placeBlock.value));
                    if (slot >= 0) { Util.select(slot); Util.place(above, null); cd = delay.value.intValue(); }
                }
            }
        }
    }
}
