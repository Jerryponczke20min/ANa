package pl.ksawu23.ana;

import com.google.gson.*;
import net.minecraft.client.MinecraftClient;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.function.Consumer;

/**
 * Klucze dostepu. Sprawdzane na serwerze kluczy (keyserver/server.js).
 * Dopoki SERVER_URL jest pusty, mod dziala bez klucza.
 */
public final class License {
    /** Adres serwera kluczy, np. "https://moj-serwer.onrender.com". Puste = brak blokady. USTAW PRZED BUDOWANIEM. */
    public static final String SERVER_URL = "https://ksawu23-keyserver.onrender.com";

    static final Gson G = new Gson();
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    static volatile boolean valid, permanent, checking;
    static volatile long expiresAt, offset;
    static String activationId = "";
    static long lastCheck;

    public static boolean gate() { return !SERVER_URL.isBlank(); }

    static long now() { return System.currentTimeMillis() + offset; }

    public static boolean ok() {
        if (!gate()) return true;
        if (!valid) return false;
        if (!permanent && now() >= expiresAt) { valid = false; return false; }
        return true;
    }

    static File file() { return new File(MinecraftClient.getInstance().runDirectory, "ksawu23/license.json"); }

    static String hwid() {
        try {
            String raw = System.getProperty("user.name") + "|" + System.getProperty("os.name") + "|"
                    + System.getenv("COMPUTERNAME") + "|" + Runtime.getRuntime().availableProcessors();
            byte[] h = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) { return "unknown"; }
    }

    public static void load() {
        if (!gate()) { valid = true; return; }
        File f = file();
        if (!f.exists()) return;
        try (Reader r = new FileReader(f)) {
            JsonObject o = JsonParser.parseReader(r).getAsJsonObject();
            activationId = o.get("activationId").getAsString();
            permanent = o.get("permanent").getAsBoolean();
            expiresAt = o.get("expiresAt").getAsLong();
            valid = permanent || System.currentTimeMillis() < expiresAt;   // wstepnie; zaraz potwierdzi serwer
            check();
        } catch (Exception ignored) {}
    }

    static void save() {
        try {
            file().getParentFile().mkdirs();
            JsonObject o = new JsonObject();
            o.addProperty("activationId", activationId);
            o.addProperty("permanent", permanent);
            o.addProperty("expiresAt", expiresAt);
            try (Writer w = new FileWriter(file())) { G.toJson(o, w); }
        } catch (Exception ignored) {}
    }

    /** HTTP POST w tle; wynik wraca na watek gry. */
    static void post(String path, JsonObject body, String adminSecret, Consumer<JsonObject> cb) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            JsonObject out;
            try {
                HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(SERVER_URL.replaceAll("/+$", "") + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(G.toJson(body)));
                if (adminSecret != null) b.header("X-Admin", adminSecret);
                HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
                out = JsonParser.parseString(r.body()).getAsJsonObject();
            } catch (Exception e) {
                out = new JsonObject();
                out.addProperty("ok", false);
                out.addProperty("error", "Brak polaczenia z serwerem kluczy");
                out.addProperty("offline", true);
            }
            final JsonObject fo = out;
            MinecraftClient.getInstance().execute(() -> cb.accept(fo));
        });
    }

    static void apply(JsonObject r) {
        if (r.has("now")) offset = r.get("now").getAsLong() - System.currentTimeMillis();
        permanent = r.has("permanent") && r.get("permanent").getAsBoolean();
        expiresAt = r.has("expiresAt") ? r.get("expiresAt").getAsLong() : 0;
        valid = true;
    }

    /** Aktywacja klucza wpisanego przez uzytkownika. */
    public static void redeem(String key, Consumer<String> done) {
        JsonObject b = new JsonObject();
        b.addProperty("key", key);
        b.addProperty("hwid", hwid());
        post("/redeem", b, null, r -> {
            if (r.has("ok") && r.get("ok").getAsBoolean()) {
                activationId = r.get("activationId").getAsString();
                apply(r);
                save();
                done.accept(null);
            } else done.accept(r.has("error") ? r.get("error").getAsString() : "Nieznany blad");
        });
    }

    /** Potwierdzenie licencji z serwerem (przy starcie i co 10 minut). */
    public static void check() {
        if (!gate() || activationId.isEmpty() || checking) return;
        checking = true;
        JsonObject b = new JsonObject();
        b.addProperty("activationId", activationId);
        b.addProperty("hwid", hwid());
        post("/check", b, null, r -> {
            checking = false;
            lastCheck = System.currentTimeMillis();
            if (r.has("ok") && r.get("ok").getAsBoolean()) { apply(r); save(); }
            else if (!(r.has("offline") && r.get("offline").getAsBoolean())) {   // serwer odpowiedzial, ze klucz juz nie dziala
                valid = false; permanent = false; activationId = "";
                file().delete();
            }
        });
    }

    public static void tick() {
        if (gate() && valid && !checking && System.currentTimeMillis() - lastCheck > 600_000) check();
    }

    public static String remaining() {
        if (!gate()) return "bez blokady (serwer kluczy nie jest ustawiony)";
        if (!valid) return "brak aktywnego klucza";
        if (permanent) return "na zawsze";
        long s = Math.max(0, (expiresAt - now()) / 1000);
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60, sec = s % 60;
        return (d > 0 ? d + "d " : "") + h + "h " + m + "m " + sec + "s";
    }

    // ---------- panel wlasciciela ----------
    public static void admin(String secret, String path, JsonObject body, Consumer<JsonObject> cb) { post(path, body, secret, cb); }

    static File ownerFile() { return new File(MinecraftClient.getInstance().runDirectory, "ksawu23/owner.txt"); }

    static String loadSecret() {
        try { return java.nio.file.Files.readString(ownerFile().toPath()).trim(); } catch (Exception e) { return ""; }
    }

    static void saveSecret(String s) {
        try { ownerFile().getParentFile().mkdirs(); java.nio.file.Files.writeString(ownerFile().toPath(), s); } catch (Exception ignored) {}
    }

    public static class Mod extends Module {
        public Mod() {
            super("Licencja / klucz", "Ustawienia");
            alwaysOn = true; enabled = true;
            action = () -> mc.setScreen(new KeyScreen());
        }
    }
}
