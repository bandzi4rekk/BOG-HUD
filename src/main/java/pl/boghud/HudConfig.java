package pl.boghud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/** Ustawienia moda, zapisywane w config/boghud.json */
public class HudConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("boghud.json");

    public static final int ELEMENTS = 5;
    public static final int WINDOWS = 7; // Ogólne + 5 elementów + Motyw GUI
    public static final String[] NAMES = {"Tekst", "FPS", "Ping", "Koordynaty", "Pancerz"};

    /** Jeden element HUD-a (osobna ramka na ekranie z własnymi ustawieniami). */
    public static class Element {
        public boolean enabled = true;
        public int x, y;
        public int scalePercent = 100;
        public int textColor = 0xFFFFFF;
        public int accentColor = 0x55FFFF;
        public boolean background = true;
        public int backgroundAlpha = 120;
        public boolean shadow = true;

        public Element() {}

        public Element(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    public boolean enabled = true;
    public String customText = "Bog HUD";
    /** Kolejność: Tekst, FPS, Ping, Koordynaty, Pancerz */
    public Element[] elements = defaultElements();

    // Motyw GUI (kolory okien ustawień)
    public int guiAccent = 0x5CE1E6;
    public int guiBackground = 0x141A22;
    public int guiAlpha = 235;
    /** Rozmiar okien ustawień w procentach (70-150). */
    public int guiScale = 100;

    // Okna GUI: pozycje (-1 = automatyczne) i zwinięcie
    public int[] winX = filled(-1);
    public int[] winY = filled(-1);
    public boolean[] winCollapsed = collapsedAll();

    private static Element[] defaultElements() {
        return new Element[]{
                new Element(4, 4),
                new Element(4, 20),
                new Element(4, 34),
                new Element(4, 48),
                new Element(4, 64)
        };
    }

    private static int[] filled(int v) {
        int[] a = new int[WINDOWS];
        Arrays.fill(a, v);
        return a;
    }

    private static boolean[] collapsedAll() {
        boolean[] a = new boolean[WINDOWS];
        Arrays.fill(a, true);
        return a;
    }

    /** Naprawia braki (np. po wczytaniu configu ze starszej wersji moda). */
    public void fixup() {
        if (customText == null) customText = "";
        if (elements == null || elements.length != ELEMENTS) {
            elements = defaultElements();
        } else {
            Element[] d = defaultElements();
            for (int i = 0; i < ELEMENTS; i++) {
                if (elements[i] == null) elements[i] = d[i];
            }
        }
        if (winX == null || winX.length != WINDOWS) winX = filled(-1);
        if (winY == null || winY.length != WINDOWS) winY = filled(-1);
        if (winCollapsed == null || winCollapsed.length != WINDOWS) winCollapsed = collapsedAll();
    }

    public static HudConfig load() {
        if (Files.exists(PATH)) {
            try (Reader r = Files.newBufferedReader(PATH)) {
                HudConfig c = GSON.fromJson(r, HudConfig.class);
                if (c != null) {
                    c.fixup();
                    return c;
                }
            } catch (Exception e) {
                System.err.println("[BogHUD] Nie udało się wczytać configu: " + e);
            }
        }
        HudConfig c = new HudConfig();
        c.save();
        return c;
    }

    public void save() {
        try (Writer w = Files.newBufferedWriter(PATH)) {
            GSON.toJson(this, w);
        } catch (Exception e) {
            System.err.println("[BogHUD] Nie udało się zapisać configu: " + e);
        }
    }
}
