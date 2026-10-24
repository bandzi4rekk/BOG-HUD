package pl.boghud;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.RotationAxis;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Ekran ustawień w stylu "ClickGUI": kilka osobnych okien (przeciągane za tytuł, +/- zwija),
 * po jednym na każdy element HUD-a, plus okno Ogólne i okno Motyw GUI (kolory GUI).
 * Elementy HUD na ekranie też można przeciągać myszką.
 */
public class HudConfigScreen extends Screen {
    private static final int WIN_W = 150;
    private static final int TITLE_H = 16;
    private static final int PAD = 4;
    private static final int RED = 0xFFE05555;
    private static final int GREEN = 0xFF55E077;
    private static final int BLUE = 0xFF5599E0;
    private static final int[] PRESETS = {0x5CE1E6, 0xB388FF, 0xFF5C7A, 0x66E08A, 0xFFB45C, 0xFFFFFF};

    private final Screen parent;
    private final List<Window> windows = new ArrayList<>();
    private TextFieldWidget field;
    private FieldRow fieldRow;

    private Window dragWindow;
    private int dragOffX, dragOffY;
    private Window activeWin;
    private Row activeRow;
    private int dragEl = -1;
    private double fx, fy;

    public HudConfigScreen(Screen parent) {
        super(Text.literal("Bog HUD"));
        this.parent = parent;
    }

    private HudConfig c() {
        return HudMod.CONFIG;
    }

    private HudConfig.Element el(int i) {
        return HudMod.CONFIG.elements[i];
    }

    // ------------------------------------------------------------------ budowa okien

    @Override
    protected void init() {
        windows.clear();
        activeWin = null;
        activeRow = null;
        dragWindow = null;
        dragEl = -1;

        field = new TextFieldWidget(this.textRenderer, 0, 0, WIN_W - 2 * PAD, 16, Text.literal("Napis"));
        field.setMaxLength(64);
        field.setText(c().customText == null ? "" : c().customText);
        field.setChangedListener(s -> c().customText = s);
        field.visible = false;
        addDrawableChild(field);
        fieldRow = new FieldRow();

        // 0: Ogólne
        Window general = new Window(0, "Ogólne");
        general.rows.add(new ToggleRow("HUD włączony", () -> c().enabled, v -> c().enabled = v));
        general.rows.add(new TextRow("Elementy HUD przeciągaj"));
        general.rows.add(new TextRow("myszką po ekranie."));
        general.rows.add(new TextRow("Okna: przeciągaj za tytuł,"));
        general.rows.add(new TextRow("+ / - zwija i rozwija."));
        general.rows.add(new ButtonRow("Reset wszystkiego", () -> {
            HudMod.CONFIG = new HudConfig();
            HudMod.CONFIG.save();
            clearAndInit();
        }));
        windows.add(general);

        // 1..5: elementy HUD
        for (int ei = 0; ei < HudConfig.ELEMENTS; ei++) {
            Window w = new Window(ei + 1, HudConfig.NAMES[ei]);
            buildElement(w, ei);
            windows.add(w);
        }

        // 6: Ustawienia GUI (rozmiar i kolory okien)
        Window theme = new Window(6, "Ustawienia GUI");
        theme.rows.add(new HeadingRow("Rozmiar GUI (%)", null));
        theme.rows.add(new ChoiceRow(new int[]{70, 85, 100, 125, 150}, () -> c().guiScale, v -> c().guiScale = v));
        theme.rows.add(new HeadingRow("Akcent GUI", () -> c().guiAccent));
        theme.rows.add(new PresetRow(PRESETS, v -> c().guiAccent = v));
        addChannels(theme, () -> c().guiAccent, v -> c().guiAccent = v);
        theme.rows.add(new HeadingRow("Tło GUI", () -> c().guiBackground));
        addChannels(theme, () -> c().guiBackground, v -> c().guiBackground = v);
        theme.rows.add(new SliderRow("Krycie", 80, 255, () -> c().guiAlpha, v -> c().guiAlpha = v, 0));
        windows.add(theme);

        // pozycje startowe (układ w rzędach pod logo), potem pilnujemy, żeby okna były na ekranie
        int x = 8, y = 44;
        for (Window w : windows) {
            if (x + WIN_W > vw() - 4 && x > 8) {
                x = 8;
                y += 22;
            }
            if (c().winX[w.idx] < 0 || c().winY[w.idx] < 0) {
                c().winX[w.idx] = x;
                c().winY[w.idx] = y;
            }
            x += WIN_W + 4;
        }
        clampWindows();
    }

    // skala okien ustawień (rozmiar GUI)
    private double s() {
        return Math.max(0.5, c().guiScale / 100.0);
    }

    private int vw() {
        return (int) Math.floor(this.width / s());
    }

    private int vh() {
        return (int) Math.floor(this.height / s());
    }

    private void clampWindows() {
        for (Window w : windows) {
            c().winX[w.idx] = clamp(c().winX[w.idx], 0, Math.max(0, vw() - WIN_W));
            c().winY[w.idx] = clamp(c().winY[w.idx], 0, Math.max(0, vh() - TITLE_H));
        }
    }

    private void buildElement(Window w, int ei) {
        if (ei == 0) {
            w.rows.add(new HeadingRow("Napis", null));
            w.rows.add(fieldRow);
        }
        w.rows.add(new ToggleRow("Włączony", () -> el(ei).enabled, v -> el(ei).enabled = v));
        w.rows.add(new SliderRow("Skala %", 50, 200, () -> el(ei).scalePercent, v -> el(ei).scalePercent = v, 0));
        w.rows.add(new HeadingRow("Kolor tekstu", () -> el(ei).textColor));
        addChannels(w, () -> el(ei).textColor, v -> el(ei).textColor = v);
        w.rows.add(new HeadingRow("Kolor akcentu", () -> el(ei).accentColor));
        addChannels(w, () -> el(ei).accentColor, v -> el(ei).accentColor = v);
        w.rows.add(new ToggleRow("Tło", () -> el(ei).background, v -> el(ei).background = v));
        w.rows.add(new SliderRow("Krycie tła", 0, 255, () -> el(ei).backgroundAlpha, v -> el(ei).backgroundAlpha = v, 0));
        w.rows.add(new ToggleRow("Cień tekstu", () -> el(ei).shadow, v -> el(ei).shadow = v));
    }

    private void addChannels(Window w, IntSupplier get, IntConsumer set) {
        w.rows.add(new SliderRow("R", 0, 255, () -> (get.getAsInt() >> 16) & 255,
                v -> set.accept((get.getAsInt() & 0x00FFFF) | (v << 16)), RED));
        w.rows.add(new SliderRow("G", 0, 255, () -> (get.getAsInt() >> 8) & 255,
                v -> set.accept((get.getAsInt() & 0xFF00FF) | (v << 8)), GREEN));
        w.rows.add(new SliderRow("B", 0, 255, () -> get.getAsInt() & 255,
                v -> set.accept((get.getAsInt() & 0xFFFF00) | v), BLUE));
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    // ------------------------------------------------------------------ rysowanie

    // Bez rozmycia i przyciemnienia, żeby HUD był widoczny na żywo
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        drawLogo(ctx);

        // ramki wokół elementów HUD (piksele ekranu, bez skalowania okien)
        for (int i = 0; i < HudConfig.ELEMENTS; i++) {
            if (HudRenderer.boundsW[i] > 0) {
                int col = (i == dragEl ? 0xFF000000 : 0x88000000) | c().guiAccent;
                Ui.outline(ctx, HudRenderer.boundsX[i] - 1, HudRenderer.boundsY[i] - 1,
                        HudRenderer.boundsW[i] + 2, HudRenderer.boundsH[i] + 2, col);
            }
        }

        clampWindows();
        float sc = (float) s();
        int vmx = (int) Math.round(mouseX / s());
        int vmy = (int) Math.round(mouseY / s());

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale(sc, sc, 1f);

        fieldRow.shown = false;
        for (Window w : windows) renderWindow(ctx, w, vmx, vmy);

        if (fieldRow.shown) {
            field.visible = true;
            field.setX(fieldRow.x);
            field.setY(fieldRow.y + 3);
            field.setWidth(fieldRow.w);
        } else {
            field.visible = false;
        }
        super.render(ctx, vmx, vmy, delta);

        m.pop();
    }

    /** Napis BOG CODE w stylu graffiti: gruby, lekko przechylony, z obrysem, cieniem i zaciekami farby. */
    private void drawLogo(DrawContext ctx) {
        TextRenderer tr = this.textRenderer;
        Text bog = Text.literal("BOG").formatted(Formatting.BOLD);
        Text code = Text.literal("CODE").formatted(Formatting.BOLD);
        int wBog = tr.getWidth(bog);
        int wCode = tr.getWidth(code);
        int gap = 5;
        int total = wBog + gap + wCode;
        int xCode = wBog + gap;
        int yellow = 0xFFFFE135;
        int red = 0xFFFF2E2E;

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.translate(this.width / 2f, 24f, 0f);
        m.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-4f));
        m.scale(3f, 3f, 1f);
        m.translate(-total / 2f, -4f, 0f);

        // cień
        drawPair(ctx, tr, bog, code, xCode, 2, 2, 0x99000000, 0x99000000);
        // gruby czarny obrys
        int[][] o = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};
        for (int[] d : o) {
            drawPair(ctx, tr, bog, code, xCode, d[0], d[1], 0xFF000000, 0xFF000000);
        }

        // zacieki farby
        int[][] dripsBog = {{2, 3}, {9, 6}, {wBog - 4, 2}};
        for (int[] d : dripsBog) drip(ctx, d[0], d[1], yellow);
        int[][] dripsCode = {{1, 5}, {8, 2}, {15, 7}, {wCode - 4, 3}};
        for (int[] d : dripsCode) drip(ctx, xCode + d[0], d[1], red);

        // właściwy napis: BOG żółty, CODE czerwony
        drawPair(ctx, tr, bog, code, xCode, 0, 0, yellow, red);

        m.pop();
    }

    private void drawPair(DrawContext ctx, TextRenderer tr, Text a, Text b, int xB, int dx, int dy, int ca, int cb) {
        ctx.drawText(tr, a, dx, dy, ca, false);
        ctx.drawText(tr, b, xB + dx, dy, cb, false);
    }

    private void drip(DrawContext ctx, int x, int len, int color) {
        int top = 8;
        ctx.fill(x, top, x + 1, top + len, color);
        ctx.fill(x - 1, top + len, x + 2, top + len + 1, color);
        ctx.fill(x, top + len + 1, x + 1, top + len + 2, color);
    }

    private void renderWindow(DrawContext ctx, Window w, int mx, int my) {
        TextRenderer tr = this.textRenderer;
        int x = w.x(), y = w.y(), h = w.height();

        ctx.fill(x - 1, y - 1, x + WIN_W + 1, y + h + 1, Ui.border());
        ctx.fill(x, y, x + WIN_W, y + h, Ui.bg());
        ctx.fill(x, y, x + WIN_W, y + TITLE_H, Ui.titleBg());
        ctx.fill(x, y + TITLE_H - 1, x + WIN_W, y + TITLE_H, Ui.accent());
        ctx.drawText(tr, w.title, x + 6, y + 4, Ui.accent(), false);
        ctx.drawText(tr, w.collapsed() ? "+" : "-", x + WIN_W - 10, y + 4, Ui.text(), false);
        if (w.collapsed()) return;

        int lw = w.labelW(tr);
        int ry = y + TITLE_H + 2;
        for (Row r : w.rows) {
            r.render(ctx, tr, x + PAD, ry, WIN_W - 2 * PAD, lw, mx, my);
            ry += r.h;
        }
    }

    // ------------------------------------------------------------------ mysz

    private Window windowAt(double mx, double my) {
        for (int i = windows.size() - 1; i >= 0; i--) {
            Window w = windows.get(i);
            if (w.contains(mx, my)) return w;
        }
        return null;
    }

    private void clickRows(Window w, double mx, double my) {
        int lw = w.labelW(this.textRenderer);
        int ry = w.y() + TITLE_H + 2;
        for (Row r : w.rows) {
            if (my >= ry && my < ry + r.h) {
                activeWin = w;
                activeRow = r;
                r.click(mx, my, w.x() + PAD, WIN_W - 2 * PAD, lw);
                return;
            }
            ry += r.h;
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        double vx = mx / s(), vy = my / s();
        if (field.visible && field.isMouseOver(vx, vy)) {
            return super.mouseClicked(vx, vy, button);
        }
        field.setFocused(false);

        Window win = windowAt(vx, vy);
        if (win != null) {
            windows.remove(win);
            windows.add(win); // na wierzch
            if (vy < win.y() + TITLE_H) {
                if (button == 1 || vx >= win.x() + WIN_W - 14) {
                    c().winCollapsed[win.idx] = !c().winCollapsed[win.idx];
                } else if (button == 0) {
                    dragWindow = win;
                    dragOffX = (int) Math.round(vx) - win.x();
                    dragOffY = (int) Math.round(vy) - win.y();
                }
                return true;
            }
            if (button == 0 && !win.collapsed()) clickRows(win, vx, vy);
            return true;
        }

        if (button == 0) {
            int hit = HudRenderer.hitAny(mx, my);
            if (hit >= 0) {
                dragEl = hit;
                fx = el(hit).x;
                fy = el(hit).y;
                return true;
            }
        }
        return super.mouseClicked(vx, vy, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        double vx = mx / s(), vy = my / s();
        if (button == 0) {
            if (dragWindow != null) {
                int nx = (int) Math.round(vx) - dragOffX;
                int ny = (int) Math.round(vy) - dragOffY;
                c().winX[dragWindow.idx] = clamp(nx, 0, Math.max(0, vw() - WIN_W));
                c().winY[dragWindow.idx] = clamp(ny, 0, Math.max(0, vh() - TITLE_H));
                return true;
            }
            if (activeRow != null && activeWin != null) {
                activeRow.drag(vx, vy, activeWin.x() + PAD, WIN_W - 2 * PAD, activeWin.labelW(this.textRenderer));
                return true;
            }
            if (dragEl >= 0) {
                fx = Math.max(0, Math.min(this.width, fx + dx));
                fy = Math.max(0, Math.min(this.height, fy + dy));
                el(dragEl).x = (int) Math.round(fx);
                el(dragEl).y = (int) Math.round(fy);
                return true;
            }
        }
        return super.mouseDragged(vx, vy, button, dx / s(), dy / s());
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragWindow = null;
        activeWin = null;
        activeRow = null;
        dragEl = -1;
        return super.mouseReleased(mx / s(), my / s(), button);
    }

    @Override
    public void removed() {
        c().save();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    // ------------------------------------------------------------------ okno

    private class Window {
        final int idx;
        final String title;
        final List<Row> rows = new ArrayList<>();

        Window(int idx, String title) {
            this.idx = idx;
            this.title = title;
        }

        int x() {
            return c().winX[idx];
        }

        int y() {
            return c().winY[idx];
        }

        boolean collapsed() {
            return c().winCollapsed[idx];
        }

        int height() {
            if (collapsed()) return TITLE_H;
            int h = TITLE_H + 4;
            for (Row r : rows) h += r.h;
            return h;
        }

        int labelW(TextRenderer tr) {
            int m = 0;
            for (Row r : rows) m = Math.max(m, r.labelWidth(tr));
            return m;
        }

        boolean contains(double mx, double my) {
            return mx >= x() && mx < x() + WIN_W && my >= y() && my < y() + height();
        }
    }

    // ------------------------------------------------------------------ wiersze

    private abstract static class Row {
        final int h;

        Row(int h) {
            this.h = h;
        }

        int labelWidth(TextRenderer tr) {
            return 0;
        }

        abstract void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my);

        void click(double mx, double my, int x, int w, int lw) {
        }

        void drag(double mx, double my, int x, int w, int lw) {
        }
    }

    private static class ToggleRow extends Row {
        private final String label;
        private final BooleanSupplier get;
        private final Consumer<Boolean> set;

        ToggleRow(String label, BooleanSupplier get, Consumer<Boolean> set) {
            super(16);
            this.label = label;
            this.get = get;
            this.set = set;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            if (mx >= x && mx < x + w && my >= y && my < y + h) {
                ctx.fill(x, y, x + w, y + h, Ui.hover());
            }
            ctx.drawText(tr, label, x + 2, y + 4, Ui.text(), false);
            boolean on = get.getAsBoolean();
            int sw = 22, sh = 10;
            int sx = x + w - sw - 2;
            int sy = y + 3;
            ctx.fill(sx, sy, sx + sw, sy + sh, on ? Ui.accent() : Ui.off());
            int kx = on ? sx + sw - sh + 1 : sx + 1;
            ctx.fill(kx, sy + 1, kx + sh - 2, sy + sh - 1, 0xFFFFFFFF);
        }

        @Override
        void click(double mx, double my, int x, int w, int lw) {
            set.accept(!get.getAsBoolean());
        }
    }

    private static class SliderRow extends Row {
        private final String label;
        private final int min, max, tint;
        private final IntSupplier get;
        private final IntConsumer set;

        SliderRow(String label, int min, int max, IntSupplier get, IntConsumer set, int tint) {
            super(14);
            this.label = label;
            this.min = min;
            this.max = max;
            this.get = get;
            this.set = set;
            this.tint = tint;
        }

        @Override
        int labelWidth(TextRenderer tr) {
            return tr.getWidth(label) + 6;
        }

        private void setFromMouse(double mx, int x, int w, int lw) {
            int tw = w - lw - 26;
            double t = (mx - (x + lw)) / tw;
            t = Math.max(0, Math.min(1, t));
            set.accept((int) Math.round(min + t * (max - min)));
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            int cy = y + h / 2;
            ctx.drawText(tr, label, x + 2, cy - 4, Ui.text(), false);
            int tx = x + lw;
            int tw = w - lw - 26;
            int v = Math.max(min, Math.min(max, get.getAsInt()));
            double t = (v - min) / (double) (max - min);
            int kx = tx + (int) Math.round(t * tw);
            ctx.fill(tx, cy - 1, tx + tw, cy + 2, Ui.off());
            ctx.fill(tx, cy - 1, kx, cy + 2, tint == 0 ? Ui.accent() : tint);
            ctx.fill(kx - 2, cy - 5, kx + 2, cy + 6, 0xFFFFFFFF);
            String s = String.valueOf(v);
            ctx.drawText(tr, s, x + w - tr.getWidth(s) - 2, cy - 4, Ui.muted(), false);
        }

        @Override
        void click(double mx, double my, int x, int w, int lw) {
            if (mx >= x + lw - 6) setFromMouse(mx, x, w, lw);
        }

        @Override
        void drag(double mx, double my, int x, int w, int lw) {
            setFromMouse(mx, x, w, lw);
        }
    }

    private static class HeadingRow extends Row {
        private final String text;
        private final IntSupplier swatch;

        HeadingRow(String text, IntSupplier swatch) {
            super(14);
            this.text = text;
            this.swatch = swatch;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            ctx.drawText(tr, text, x + 2, y + 3, Ui.accent(), false);
            if (swatch != null) {
                int sx = x + w - 22;
                ctx.fill(sx - 1, y + 1, sx + 19, y + 13, Ui.border());
                ctx.fill(sx, y + 2, sx + 18, y + 12, 0xFF000000 | swatch.getAsInt());
            }
        }
    }

    private static class TextRow extends Row {
        private final String text;

        TextRow(String text) {
            super(11);
            this.text = text;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            ctx.drawText(tr, text, x + 2, y + 2, Ui.muted(), false);
        }
    }

    private static class ButtonRow extends Row {
        private final String label;
        private final Runnable action;

        ButtonRow(String label, Runnable action) {
            super(20);
            this.label = label;
            this.action = action;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            boolean hover = mx >= x && mx < x + w && my >= y + 2 && my < y + h;
            ctx.fill(x, y + 2, x + w, y + h - 1, hover ? Ui.border() : Ui.off());
            ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + 7, Ui.text(), false);
        }

        @Override
        void click(double mx, double my, int x, int w, int lw) {
            action.run();
        }
    }

    private static class PresetRow extends Row {
        private final int[] colors;
        private final IntConsumer set;

        PresetRow(int[] colors, IntConsumer set) {
            super(18);
            this.colors = colors;
            this.set = set;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            for (int i = 0; i < colors.length; i++) {
                int bx = x + 2 + i * 16;
                ctx.fill(bx, y + 3, bx + 12, y + 15, 0xFF000000 | colors[i]);
                if (mx >= bx && mx < bx + 12 && my >= y + 3 && my < y + 15) {
                    Ui.outline(ctx, bx - 1, y + 2, 14, 14, Ui.text());
                }
            }
        }

        @Override
        void click(double mx, double my, int x, int w, int lw) {
            double rel = mx - (x + 2);
            if (rel < 0) return;
            int i = (int) (rel / 16);
            if (i < colors.length && rel - i * 16 < 12) set.accept(colors[i]);
        }
    }

    /** Rząd przycisków wyboru jednej wartości (np. rozmiar GUI). */
    private static class ChoiceRow extends Row {
        private final int[] values;
        private final IntSupplier get;
        private final IntConsumer set;

        ChoiceRow(int[] values, IntSupplier get, IntConsumer set) {
            super(18);
            this.values = values;
            this.get = get;
            this.set = set;
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            int cw = w / values.length;
            for (int i = 0; i < values.length; i++) {
                int bx = x + i * cw;
                boolean sel = get.getAsInt() == values[i];
                boolean hover = mx >= bx && mx < bx + cw && my >= y + 2 && my < y + h - 2;
                ctx.fill(bx + 1, y + 2, bx + cw - 1, y + h - 2, sel ? Ui.accent() : (hover ? Ui.border() : Ui.off()));
                String t = String.valueOf(values[i]);
                ctx.drawText(tr, t, bx + (cw - tr.getWidth(t)) / 2, y + 5, sel ? Ui.onAccent() : Ui.text(), false);
            }
        }

        @Override
        void click(double mx, double my, int x, int w, int lw) {
            int cw = w / values.length;
            int i = (int) ((mx - x) / cw);
            if (i >= 0 && i < values.length) set.accept(values[i]);
        }
    }

    /** Miejsce na pole tekstowe (samo pole jest zwykłym widgetem ustawianym w render). */
    private static class FieldRow extends Row {
        boolean shown;
        int x, y, w;

        FieldRow() {
            super(22);
        }

        @Override
        void render(DrawContext ctx, TextRenderer tr, int x, int y, int w, int lw, int mx, int my) {
            this.shown = true;
            this.x = x;
            this.y = y;
            this.w = w;
        }
    }
}
