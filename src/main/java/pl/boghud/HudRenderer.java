package pl.boghud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rysuje każdy element HUD-a jako osobną ramkę z własnymi ustawieniami. */
public final class HudRenderer {
    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    /** Ostatnio narysowane obszary elementów (piksele GUI) - do przeciągania myszką. */
    public static final int[] boundsX = new int[HudConfig.ELEMENTS];
    public static final int[] boundsY = new int[HudConfig.ELEMENTS];
    public static final int[] boundsW = new int[HudConfig.ELEMENTS];
    public static final int[] boundsH = new int[HudConfig.ELEMENTS];

    private HudRenderer() {}

    /** Zwraca indeks elementu pod kursorem (najwyższy z wierzchu) albo -1. */
    public static int hitAny(double mx, double my) {
        for (int i = HudConfig.ELEMENTS - 1; i >= 0; i--) {
            if (boundsW[i] > 0 && mx >= boundsX[i] && mx <= boundsX[i] + boundsW[i]
                    && my >= boundsY[i] && my <= boundsY[i] + boundsH[i]) {
                return i;
            }
        }
        return -1;
    }

    public static void render(DrawContext ctx, RenderTickCounter tickCounter) {
        for (int i = 0; i < HudConfig.ELEMENTS; i++) {
            boundsW[i] = 0;
            boundsH[i] = 0;
        }
        HudConfig c = HudMod.CONFIG;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (c == null || !c.enabled || mc.player == null || mc.options.hudHidden) return;

        TextRenderer tr = mc.textRenderer;
        for (int i = 0; i < HudConfig.ELEMENTS; i++) {
            HudConfig.Element e = c.elements[i];
            if (!e.enabled) continue;

            List<String[]> rows = new ArrayList<>();
            List<ItemStack> armor = new ArrayList<>();

            switch (i) {
                case 0:
                    if (c.customText != null && !c.customText.isEmpty()) {
                        rows.add(new String[]{c.customText, ""});
                    }
                    break;
                case 1:
                    rows.add(new String[]{"FPS:", String.valueOf(mc.getCurrentFps())});
                    break;
                case 2: {
                    int ping = 0;
                    if (mc.getNetworkHandler() != null) {
                        PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                        if (entry != null) ping = entry.getLatency();
                    }
                    rows.add(new String[]{"Ping:", ping + " ms"});
                    break;
                }
                case 3:
                    rows.add(new String[]{"XYZ:", String.format(Locale.ROOT, "%.1f / %.1f / %.1f",
                            mc.player.getX(), mc.player.getY(), mc.player.getZ())});
                    break;
                default:
                    for (EquipmentSlot slot : ARMOR) {
                        ItemStack stack = mc.player.getEquippedStack(slot);
                        if (!stack.isEmpty()) armor.add(stack);
                    }
                    break;
            }
            drawBox(ctx, tr, e, i, rows, armor);
        }
    }

    private static void drawBox(DrawContext ctx, TextRenderer tr, HudConfig.Element e, int idx,
                                List<String[]> rows, List<ItemStack> armor) {
        if (rows.isEmpty() && armor.isEmpty()) return;

        int accent = 0xFF000000 | e.accentColor;
        int text = 0xFF000000 | e.textColor;
        int lineH = tr.fontHeight + 1;
        int armorH = 18;
        int pad = 3;

        int w = 0;
        for (String[] r : rows) {
            int rw = tr.getWidth(r[0]) + (r[1].isEmpty() ? 0 : tr.getWidth(" " + r[1]));
            w = Math.max(w, rw);
        }
        for (ItemStack s : armor) {
            w = Math.max(w, 20 + tr.getWidth(durabilityText(s)));
        }
        int h = rows.size() * lineH + armor.size() * armorH;
        if (w == 0 || h == 0) return;

        float scale = Math.max(0.5f, e.scalePercent / 100f);
        boundsX[idx] = Math.round(e.x - pad * scale);
        boundsY[idx] = Math.round(e.y - pad * scale);
        boundsW[idx] = Math.round((w + 2 * pad) * scale);
        boundsH[idx] = Math.round((h + 2 * pad) * scale);

        MatrixStack m = ctx.getMatrices();
        m.push();
        m.scale(scale, scale, 1f);

        int x = Math.round(e.x / scale);
        int y = Math.round(e.y / scale);

        if (e.background && e.backgroundAlpha > 0) {
            ctx.fill(x - pad, y - pad, x + w + pad, y + h + pad, e.backgroundAlpha << 24);
        }

        int cy = y;
        for (String[] r : rows) {
            ctx.drawText(tr, r[0], x, cy, accent, e.shadow);
            if (!r[1].isEmpty()) {
                ctx.drawText(tr, r[1], x + tr.getWidth(r[0] + " "), cy, text, e.shadow);
            }
            cy += lineH;
        }
        for (ItemStack s : armor) {
            ctx.drawItem(s, x, cy);
            ctx.drawText(tr, durabilityText(s), x + 20, cy + 4, text, e.shadow);
            cy += armorH;
        }

        m.pop();
    }

    private static String durabilityText(ItemStack s) {
        if (!s.isDamageable()) return "";
        return (s.getMaxDamage() - s.getDamage()) + "/" + s.getMaxDamage();
    }
}
