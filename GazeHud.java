package com.example.elementalgaze.client;

import com.example.elementalgaze.ClientKit;
import com.example.elementalgaze.Element;
import com.example.elementalgaze.GazeCharacter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;

/** 상단 경험치 바 + 우측 하단 캐릭터 카드(스킬 칸 3개). 값이 바뀔 때만 서버가 동기화하므로 렌더에서는 계산만 한다. */
public final class GazeHud {
    private static final int PANEL = 0xC0101018;
    private static final int MUTED = 0xFF8A8FA8;
    private static final int GOLD = 0xFFFFD75E;

    private GazeHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        Font f = mc.font;
        long now = mc.level.getGameTime();
        int accent = ClientKit.element >= 0 ? (0xFF000000 | Element.VALUES[ClientKit.element].color) : 0xFFB8BDD1;
        topBar(g, f, w, accent, now);
        if (ClientKit.stage < 2) energyCard(g, f, w, h, accent);
        else skillCard(g, f, w, h, now, accent);
    }

    // ---------------- 상단: 레벨 / 경험치 ----------------
    private static void topBar(GuiGraphics g, Font f, int w, int accent, long now) {
        int bw = 180, bh = 5, x = (w - bw) / 2, y = 6;
        float frac = ClientKit.xpNeed > 0 ? Math.min(1f, ClientKit.xp / ClientKit.xpNeed) : 0f;
        int fw = (int) (bw * frac);
        g.fill(x - 1, y - 1, x + bw + 1, y + bh + 1, 0xB0000000);
        g.fill(x, y, x + bw, y + bh, 0xFF1B1D28);
        if (fw > 0) {
            g.fill(x, y, x + fw, y + bh, accent);
            g.fill(x, y, x + fw, y + 1, 0x66FFFFFF);
        }
        String lv = "Lv." + ClientKit.level;
        g.drawString(f, lv, x - f.width(lv) - 6, y - 1, 0xFFFFFFFF, true);
        String xp = (int) ClientKit.xp + " / " + (int) ClientKit.xpNeed;
        g.drawString(f, xp, x + bw + 6, y - 1, 0xFFB8BDD1, true);
        if (ClientKit.points > 0) {
            int a = 170 + (int) (Math.sin(now * 0.3) * 70);
            String s = "★ " + ClientKit.points + "  [" + Keys.MENU.getTranslatedKeyMessage().getString() + "]";
            g.drawCenteredString(f, s, w / 2, y + bh + 4, (a << 24) | 0xFFD75E);
        }
    }

    // ---------------- 발현 전: 에너지 카드 ----------------
    private static void energyCard(GuiGraphics g, Font f, int w, int h, int accent) {
        int pw = 150, ph = 34, x = w - pw - 10, y = h - ph - 10;
        g.fill(x, y, x + pw, y + ph, PANEL);
        g.fill(x, y, x + 2, y + ph, accent);
        float frac = ClientKit.energyMax > 0 ? Math.min(1f, ClientKit.energy / ClientKit.energyMax) : 0f;
        String key = ClientKit.stage == 0 ? "hud.elementalgaze.gaze" : "hud.elementalgaze.element";
        g.drawString(f, Component.translatable(key, (int) (frac * 100f)), x + 8, y + 6, 0xFFE6E8F2, true);
        g.fill(x + 8, y + 21, x + pw - 8, y + 26, 0xFF1B1D28);
        g.fill(x + 8, y + 21, x + 8 + (int) ((pw - 16) * frac), y + 26, accent);
    }

    // ---------------- 발현 후: 캐릭터 카드 ----------------
    private static void skillCard(GuiGraphics g, Font f, int w, int h, long now, int accent) {
        int s = 32, gap = 6;
        int pw = 8 + 3 * s + 2 * gap + 8, ph = 78;
        int x = w - pw - 10, y = h - ph - 10;
        g.fill(x, y, x + pw, y + ph, PANEL);
        g.fill(x, y, x + 2, y + ph, accent);

        g.drawString(f, Component.translatable(GazeCharacter.nameKey(ClientKit.characterId)), x + 8, y + 5, accent, true);
        String stars = "★".repeat(Math.max(0, ClientKit.rarity));
        g.drawString(f, stars, x + pw - 6 - f.width(stars), y + 5, GOLD, true);
        Element el = ClientKit.element >= 0 ? Element.VALUES[ClientKit.element] : Element.PYRO;
        g.drawString(f, Component.translatable("element.elementalgaze." + el.id).append(ClientKit.archon ? " ★" : ""),
                x + 8, y + 17, MUTED, false);

        int cy = y + 32;
        int cx = x + 8;

        // 전투 모드
        cell(g, f, cx, cy, s, "ATK", Keys.COMBAT.getTranslatedKeyMessage().getString(), 0f, -1f,
                ClientKit.combat, accent, "", "");

        // 원소 전투 스킬
        long sLeft = ClientKit.skillAt - now;
        float sFrac = sLeft > 0 ? Math.min(1f, (float) sLeft / Math.max(1, ClientKit.skillCd)) : 0f;
        cell(g, f, cx + s + gap, cy, s, "E", Keys.SKILL.getTranslatedKeyMessage().getString(), sFrac, -1f,
                sLeft <= 0, accent, secs(sLeft), "Lv." + (1 + ClientKit.up[1]));

        // 원소 폭발
        long bLeft = ClientKit.burstAt - now;
        float bFrac = bLeft > 0 ? Math.min(1f, (float) bLeft / Math.max(1, ClientKit.burstCd)) : 0f;
        float eFrac = ClientKit.burstCost > 0 ? Math.min(1f, ClientKit.burst / ClientKit.burstCost) : 0f;
        cell(g, f, cx + 2 * (s + gap), cy, s, "Q", Keys.BURST.getTranslatedKeyMessage().getString(), bFrac, eFrac,
                eFrac >= 1f && bLeft <= 0, accent, secs(bLeft), "Lv." + (1 + ClientKit.up[2]));
    }

    private static String secs(long ticks) {
        return ticks > 0 ? String.format(Locale.ROOT, "%.1f", ticks / 20.0) : "";
    }

    /** cdFrac: 남은 쿨다운 비율(0~1), energyFrac: 0~1 이면 아래에서 차오르는 에너지 표시, -1 이면 없음 */
    private static void cell(GuiGraphics g, Font f, int x, int y, int s, String label, String key,
                             float cdFrac, float energyFrac, boolean ready, int accent, String cdText, String lvText) {
        int border = ready ? accent : 0xFF454859;
        g.fill(x - 1, y - 1, x + s + 1, y + s + 1, border);
        g.fillGradient(x, y, x + s, y + s, 0xFF232636, 0xFF14151E);
        if (energyFrac >= 0f) {
            int eh = (int) ((s - 2) * energyFrac);
            g.fill(x + 1, y + s - 1 - eh, x + s - 1, y + s - 1, (accent & 0x00FFFFFF) | 0x66000000);
        }
        g.drawCenteredString(f, label, x + s / 2, y + s / 2 - 4, ready ? 0xFFFFFFFF : MUTED);
        if (cdFrac > 0f) {
            g.fill(x, y, x + s, y + (int) (s * cdFrac), 0xA0000000);
            g.drawCenteredString(f, cdText, x + s / 2, y + s / 2 - 4, 0xFFFFFFFF);
        }
        small(g, f, key, x + s / 2, y + s + 3, 0xFFB8BDD1);
        if (!lvText.isEmpty()) small(g, f, lvText, x + s - 9, y + 3, 0xFFB8BDD1);
    }

    private static void small(GuiGraphics g, Font f, String text, int centerX, int y, int color) {
        g.pose().pushPose();
        g.pose().scale(0.75f, 0.75f, 1f);
        int px = (int) ((centerX - f.width(text) * 0.75f / 2f) / 0.75f);
        g.drawString(f, text, px, (int) (y / 0.75f), color, false);
        g.pose().popPose();
    }
}
