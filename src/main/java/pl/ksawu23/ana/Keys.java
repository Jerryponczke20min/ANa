package pl.ksawu23.ana;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import java.util.*;

/** Okno wpisywania klucza dostepu (z logo). */
class KeyScreen extends Screen {
    static String keyS = "";
    String status = "";
    int statusCol = 0xFFFF5555;
    boolean busy;

    KeyScreen() { super(Text.literal("Klucz dostepu")); }

    @Override
    protected void init() {
        int cx = width / 2, y = height / 2 + 6;
        TextFieldWidget f = new TextFieldWidget(textRenderer, cx - 120, y, 240, 20, Text.literal(""));
        f.setMaxLength(64); f.setText(keyS); f.setChangedListener(s -> keyS = s);
        addDrawableChild(f);
        addDrawableChild(ButtonWidget.builder(Text.literal("Aktywuj klucz"), b -> activate())
                .dimensions(cx - 120, y + 26, 240, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Panel wlasciciela"), b -> client.setScreen(new OwnerScreen()))
                .dimensions(cx - 120, height - 30, 118, 18).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Zamknij"), b -> close())
                .dimensions(cx + 2, height - 30, 118, 18).build());
    }

    void activate() {
        if (!License.gate()) { status = "Serwer kluczy nie jest ustawiony - mod dziala bez klucza."; return; }
        if (busy || keyS.isBlank()) return;
        busy = true;
        status = "Sprawdzam klucz..."; statusCol = 0xFFAAAAAA;
        License.redeem(keyS, err -> {
            busy = false;
            if (err == null) {
                status = "Aktywowano! Dostep: " + License.remaining(); statusCol = 0xFF55FF55;
                client.setScreen(new ClickGui());
            } else { status = err; statusCol = 0xFFFF5555; }
        });
    }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        super.render(c, mx, my, dt);
        int cx = width / 2;
        int lw = Math.min(300, width - 40);
        int lh = Logo.draw(c, cx - lw / 2, 18, lw);
        if (lh == 0) c.drawCenteredTextWithShadow(textRenderer, "KSAWU23 CLIENT ANA", cx, 30, Theme.color());
        int y = 18 + Math.max(lh, 20) + 10;
        c.drawCenteredTextWithShadow(textRenderer, "more than just a client", cx, y, 0xFF7A7F88);
        boolean ok = License.ok() && License.gate();
        c.drawCenteredTextWithShadow(textRenderer, ok ? "Dostep aktywny: " + License.remaining() : "Wpisz klucz dostepu",
                cx, height / 2 - 12, ok ? 0xFF55FF55 : 0xFFFFFFFF);
        if (!status.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, status, cx, height / 2 + 56, statusCol);
        if (!License.gate())
            c.drawCenteredTextWithShadow(textRenderer, "Tryb bez blokady: SERVER_URL w License.java jest pusty.", cx, height / 2 + 72, 0xFF888888);
    }
}

/** Panel wlasciciela: tworzenie, podglad i usuwanie kluczy (wymaga sekretu administratora z serwera). */
class OwnerScreen extends Screen {
    static final List<String> UNITS = List.of("sekund", "minut", "godzin", "dni", "miesiecy", "lat", "na zawsze");
    static final List<String> CODES = List.of("s", "m", "h", "d", "M", "y", "perm");
    static String secret, keyText = "", amountS = "1", usesS = "1";
    static int unitIdx = 3;
    static List<JsonObject> keys = new ArrayList<>();
    static String status = "";
    static int statusCol = 0xFFAAAAAA;

    OwnerScreen() {
        super(Text.literal("Panel wlasciciela"));
        if (secret == null) secret = License.loadSecret();
    }

    int rows() { return Math.max(1, (height - 184 - 12) / 20); }

    TextFieldWidget field(int x, int y, int w, String init, java.util.function.Consumer<String> l) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, x, y, w, 18, Text.literal(""));
        f.setMaxLength(64); f.setText(init); f.setChangedListener(l);
        addDrawableChild(f);
        return f;
    }

    @Override
    protected void init() {
        int cx = width / 2;
        field(cx - 150, 38, 300, secret, s -> secret = s);
        field(cx - 150, 76, 300, keyText, s -> keyText = s);
        field(cx - 150, 116, 70, amountS, s -> amountS = s);
        addDrawableChild(ButtonWidget.builder(Text.literal(UNITS.get(unitIdx)), b -> {
            unitIdx = (unitIdx + 1) % UNITS.size(); clearAndInit();
        }).dimensions(cx - 76, 115, 150, 20).build());
        field(cx + 80, 116, 70, usesS, s -> usesS = s);
        addDrawableChild(ButtonWidget.builder(Text.literal("Utworz klucz"), b -> create())
                .dimensions(cx - 150, 142, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Odswiez liste"), b -> refresh())
                .dimensions(cx + 2, 142, 148, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Wroc"), b -> client.setScreen(new KeyScreen()))
                .dimensions(cx + 100, 10, 50, 16).build());

        for (int i = 0; i < Math.min(keys.size(), rows()); i++) {
            JsonObject k = keys.get(i);
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> {
                JsonObject body = new JsonObject();
                body.addProperty("key", k.get("key").getAsString());
                License.admin(secret, "/admin/delete", body, r -> { handle(r, "Usunieto klucz."); refresh(); });
            }).dimensions(cx - 150, 184 + i * 20, 20, 18).build());
        }
    }

    void handle(JsonObject r, String okMsg) {
        if (r.has("ok") && r.get("ok").getAsBoolean()) {
            License.saveSecret(secret);
            status = okMsg; statusCol = 0xFF55FF55;
        } else {
            status = r.has("error") ? r.get("error").getAsString() : "Blad"; statusCol = 0xFFFF5555;
        }
    }

    void create() {
        try {
            String code = CODES.get(unitIdx);
            int amount = code.equals("perm") ? 0 : Integer.parseInt(amountS.trim());
            int uses = Integer.parseInt(usesS.trim());
            if (keyText.isBlank()) { status = "Wpisz tekst klucza."; statusCol = 0xFFFF5555; return; }
            if (!code.equals("perm") && amount <= 0) { status = "Ilosc czasu musi byc wieksza od 0."; statusCol = 0xFFFF5555; return; }
            JsonObject body = new JsonObject();
            body.addProperty("key", keyText.trim());
            body.addProperty("amount", amount);
            body.addProperty("unit", code);
            body.addProperty("maxUses", Math.max(0, uses));
            status = "Tworze klucz..."; statusCol = 0xFFAAAAAA;
            License.admin(secret, "/admin/create", body, r -> { handle(r, "Utworzono klucz: " + keyText.trim()); refresh(); });
        } catch (Exception e) { status = "Ilosc i limit uzyc musza byc liczbami."; statusCol = 0xFFFF5555; }
    }

    void refresh() {
        License.admin(secret, "/admin/list", new JsonObject(), r -> {
            if (r.has("ok") && r.get("ok").getAsBoolean()) {
                keys = new ArrayList<>();
                JsonArray a = r.getAsJsonArray("keys");
                for (JsonElement e : a) keys.add(e.getAsJsonObject());
                clearAndInit();
            } else handle(r, "");
        });
    }

    static String fmtDur(long s) {
        if (s >= 2592000 && s % 2592000 == 0) return s / 2592000 + " mies.";
        if (s >= 86400 && s % 86400 == 0) return s / 86400 + " dni";
        if (s >= 3600 && s % 3600 == 0) return s / 3600 + " godz.";
        if (s >= 60 && s % 60 == 0) return s / 60 + " min";
        return s + " s";
    }

    @Override
    public void render(DrawContext c, int mx, int my, float dt) {
        super.render(c, mx, my, dt);
        int cx = width / 2;
        c.drawCenteredTextWithShadow(textRenderer, "Panel wlasciciela - generator kluczy", cx, 14, Theme.color());
        c.drawTextWithShadow(textRenderer, "Sekret administratora (z serwera)", cx - 150, 28, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "Tekst klucza (to wpisuje uzytkownik)", cx - 150, 66, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "Ile", cx - 150, 106, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "Jednostka czasu (klik)", cx - 74, 106, 0xFFAAAAAA);
        c.drawTextWithShadow(textRenderer, "Ile uzyc (0 = bez limitu)", cx + 30, 106, 0xFFAAAAAA);
        if (!status.isEmpty()) c.drawCenteredTextWithShadow(textRenderer, status, cx, 168, statusCol);
        if (!License.gate()) c.drawCenteredTextWithShadow(textRenderer, "SERVER_URL w License.java jest pusty - ustaw adres serwera i zbuduj moda.", cx, 180, 0xFFFFAA55);
        for (int i = 0; i < Math.min(keys.size(), rows()); i++) {
            JsonObject k = keys.get(i);
            boolean perm = k.get("permanent").getAsBoolean();
            int maxU = k.get("maxUses").getAsInt();
            String t = k.get("key").getAsString() + "  |  " + (perm ? "na zawsze" : fmtDur(k.get("durationSec").getAsLong()))
                    + "  |  uzycia " + k.get("uses").getAsInt() + "/" + (maxU == 0 ? "bez limitu" : maxU);
            c.drawTextWithShadow(textRenderer, textRenderer.trimToWidth(t, 270), cx - 126, 189 + i * 20, 0xFFFFFFFF);
        }
    }
}
