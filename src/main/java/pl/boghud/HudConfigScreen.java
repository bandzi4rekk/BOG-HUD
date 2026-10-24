package pl.boghud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Ekran ustawień: przełączniki, suwaki kolorów, pozycja, skala i własny tekst. */
public class HudConfigScreen extends Screen {
    private final Screen parent;
    private final HudConfig c = HudMod.CONFIG;
    private int labelX, labelY;

    public HudConfigScreen(Screen parent) {
        super(Text.literal("Bog HUD"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int w = 100, gap = 5, rowH = 22, top = 30;
        int col1 = this.width / 2 - (3 * w + 2 * gap) / 2;
        int col2 = col1 + w + gap;
        int col3 = col2 + w + gap;

        // Kolumna 1: przełączniki
        int y = top;
        addDrawableChild(toggle(col1, y, w, "HUD", () -> c.enabled, v -> c.enabled = v)); y += rowH;
        addDrawableChild(toggle(col1, y, w, "FPS", () -> c.showFps, v -> c.showFps = v)); y += rowH;
        addDrawableChild(toggle(col1, y, w, "Ping", () -> c.showPing, v -> c.showPing = v)); y += rowH;
        addDrawableChild(toggle(col1, y, w, "Koordy", () -> c.showCoords, v -> c.showCoords = v)); y += rowH;
        addDrawableChild(toggle(col1, y, w, "Pancerz", () -> c.showArmor, v -> c.showArmor = v)); y += rowH;
        addDrawableChild(toggle(col1, y, w, "Tekst", () -> c.showText, v -> c.showText = v)); y += rowH;
        addDrawableChild(toggle(col1, y, w, "Cień", () -> c.shadow, v -> c.shadow = v)); y += rowH;
        addDrawableChild(toggle(col1, y, w, "Tło", () -> c.background, v -> c.background = v));

        // Kolumna 2: kolory
        y = top;
        addDrawableChild(new ConfigSlider(col2, y, w, 20, "Tekst R", 0, 255, (c.textColor >> 16) & 255,
                v -> c.textColor = (c.textColor & 0x00FFFF) | (v << 16))); y += rowH;
        addDrawableChild(new ConfigSlider(col2, y, w, 20, "Tekst G", 0, 255, (c.textColor >> 8) & 255,
                v -> c.textColor = (c.textColor & 0xFF00FF) | (v << 8))); y += rowH;
        addDrawableChild(new ConfigSlider(col2, y, w, 20, "Tekst B", 0, 255, c.textColor & 255,
                v -> c.textColor = (c.textColor & 0xFFFF00) | v)); y += rowH;
        addDrawableChild(new ConfigSlider(col2, y, w, 20, "Akcent R", 0, 255, (c.accentColor >> 16) & 255,
                v -> c.accentColor = (c.accentColor & 0x00FFFF) | (v << 16))); y += rowH;
        addDrawableChild(new ConfigSlider(col2, y, w, 20, "Akcent G", 0, 255, (c.accentColor >> 8) & 255,
                v -> c.accentColor = (c.accentColor & 0xFF00FF) | (v << 8))); y += rowH;
        addDrawableChild(new ConfigSlider(col2, y, w, 20, "Akcent B", 0, 255, c.accentColor & 255,
                v -> c.accentColor = (c.accentColor & 0xFFFF00) | v)); y += rowH;
        addDrawableChild(new ConfigSlider(col2, y, w, 20, "Tło alfa", 0, 255, c.backgroundAlpha,
                v -> c.backgroundAlpha = v));

        // Kolumna 3: skala, pozycja, własny tekst
        y = top;
        addDrawableChild(new ConfigSlider(col3, y, w, 20, "Skala %", 50, 200, c.scalePercent,
                v -> c.scalePercent = v)); y += rowH;
        addDrawableChild(new ConfigSlider(col3, y, w, 20, "X", 0, Math.max(1, this.width), c.x,
                v -> c.x = v)); y += rowH;
        addDrawableChild(new ConfigSlider(col3, y, w, 20, "Y", 0, Math.max(1, this.height), c.y,
                v -> c.y = v)); y += rowH;

        labelX = col3;
        labelY = y + 2;
        TextFieldWidget field = new TextFieldWidget(this.textRenderer, col3, y + 12, w, 20, Text.literal("Własny tekst"));
        field.setMaxLength(64);
        field.setText(c.customText == null ? "" : c.customText);
        field.setChangedListener(s -> c.customText = s);
        addDrawableChild(field);

        // Gotowe
        addDrawableChild(ButtonWidget.builder(Text.literal("Gotowe"), b -> close())
                .dimensions(this.width / 2 - 100, top + 8 * rowH + 8, 200, 20).build());
    }

    private ButtonWidget toggle(int x, int y, int w, String name, BooleanSupplier get, Consumer<Boolean> set) {
        return ButtonWidget.builder(toggleText(name, get.getAsBoolean()), btn -> {
            set.accept(!get.getAsBoolean());
            btn.setMessage(toggleText(name, get.getAsBoolean()));
        }).dimensions(x, y, w, 20).build();
    }

    private static Text toggleText(String name, boolean on) {
        return Text.literal(name + ": " + (on ? "ON" : "OFF"));
    }

    // Bez rozmycia/przyciemnienia, żeby było widać podgląd HUD-a na żywo
    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        ctx.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 12, 0xFFFFFFFF);
        ctx.drawTextWithShadow(this.textRenderer, "Własny tekst:", labelX, labelY, 0xFFAAAAAA);
    }

    @Override
    public void removed() {
        c.save();
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(parent);
    }

    /** Suwak na liczbach całkowitych. */
    private static class ConfigSlider extends SliderWidget {
        private final String label;
        private final int min, max;
        private final IntConsumer setter;

        ConfigSlider(int x, int y, int w, int h, String label, int min, int max, int current, IntConsumer setter) {
            super(x, y, w, h, Text.empty(), (Math.min(Math.max(current, min), max) - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            updateMessage();
        }

        private int intValue() {
            return (int) Math.round(min + this.value * (max - min));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(label + ": " + intValue()));
        }

        @Override
        protected void applyValue() {
            setter.accept(intValue());
        }
    }
}
