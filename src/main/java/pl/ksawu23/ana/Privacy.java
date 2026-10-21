package pl.ksawu23.ana;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import java.util.Optional;

public final class Privacy {

    /** Nick gracza do wyswietlenia (Twoj prawdziwy nick zamieniany jest na pseudonim przy wlaczonym Incognito). */
    static String nick(PlayerEntity p) {
        String n = p.getName().getString();
        var inc = Modules.incognito;
        if (inc != null && inc.enabled && n.equals(inc.real())) return inc.alias.value;
        return n;
    }

    /** Incognito: lokalnie podmienia Twoj prawdziwy nick na pseudonim (czat, nametagi). Serwer nadal widzi prawdziwy nick. */
    public static class Incognito extends Module {
        final Setting.Str alias = add(new Setting.Str("Pseudonim (klik)", "Gracz"));
        final Setting.Bool hideCoords = add(new Setting.Bool("Ukryj koordynaty w HUD", true));

        Incognito() { super("Incognito", "Anarchia"); }

        String real() { return mc.getSession().getUsername(); }

        public boolean needs(Text t) { return t.getString().contains(real()); }

        public Text swap(Text t) {
            String real = real(), al = alias.value;
            MutableText out = Text.empty();
            t.<Object>visit((style, s) -> {
                out.append(Text.literal(s.replace(real, al)).setStyle(style));
                return Optional.empty();
            }, Style.EMPTY);
            return out;
        }
    }
}

class AliasScreen extends Screen {
    final Setting.Str st;
    String tmp;

    AliasScreen(Setting.Str st) { super(Text.literal("Pseudonim")); this.st = st; this.tmp = st.value; }

    @Override
    protected void init() {
        int cx = width / 2;
        TextFieldWidget f = new TextFieldWidget(textRenderer, cx - 100, 60, 200, 20, Text.literal(""));
        f.setMaxLength(16); f.setText(tmp); f.setChangedListener(s -> tmp = s);
        addDrawableChild(f);
        addDrawableChild(ButtonWidget.builder(Text.literal("Zapisz"), b -> {
            if (!tmp.isBlank()) st.value = tmp.trim();
            Config.save();
            close();
        }).dimensions(cx - 100, 90, 98, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Anuluj"), b -> close()).dimensions(cx + 2, 90, 98, 20).build());
    }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        super.render(c, mx, my, dt);
        int cx = width / 2;
        c.drawCenteredTextWithShadow(textRenderer, "Pseudonim Incognito", cx, 30, Theme.color());
        c.drawCenteredTextWithShadow(textRenderer, "Wpisz nick, ktory ma sie pokazywac zamiast Twojego prawdziwego.", cx, 44, 0xFFAAAAAA);
        c.drawCenteredTextWithShadow(textRenderer, "To dziala tylko u Ciebie (czat, nametagi) - serwer widzi prawdziwy nick.", cx, 124, 0xFF888888);
        c.drawCenteredTextWithShadow(textRenderer, "Do zmiany nicku na serwerach offline sluzy mod IAS z Twojego profilu.", cx, 136, 0xFF888888);
    }
}
