package com.example.elementalgaze.client;

import com.example.elementalgaze.AllocatePacket;
import com.example.elementalgaze.ClientKit;
import com.example.elementalgaze.Element;
import com.example.elementalgaze.Levels;
import com.example.elementalgaze.Net;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** 성장 화면 (기본 키 G). 버튼은 서버에 요청만 보내고, 결과는 동기화 패킷으로 돌아온다. */
public class GazeScreen extends Screen {
    private static final String[] NAMES = {"normal", "skill", "burst", "atk", "hp", "crit", "mastery"};
    private static final int PW = 280, PH = 236;

    private final Button[] plus = new Button[Levels.SLOTS];
    private int px, py;

    public GazeScreen() {
        super(Component.translatable("gui.elementalgaze.title"));
    }

    private int rowY(int i) {
        return py + 88 + i * 18 + (i >= 3 ? 16 : 0);
    }

    @Override
    protected void init() {
        px = (width - PW) / 2;
        py = (height - PH) / 2;
        for (int i = 0; i < Levels.SLOTS; i++) {
            final int slot = i;
            plus[i] = addRenderableWidget(Button.builder(Component.literal("+"),
                    b -> Net.CH.sendToServer(new AllocatePacket(slot)))
                    .bounds(px + PW - 36, rowY(i) - 4, 20, 16).build());
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (Keys.MENU.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private static String value(int i) {
        byte[] up = ClientKit.up;
        return switch (i) {
            case 0, 1, 2 -> "Lv." + (1 + up[i]) + " / " + (1 + Levels.cap(i));
            case 3 -> "+" + Math.round((Levels.atkMul(up, ClientKit.level) - 1f) * 100f) + "%";
            case 4 -> "+" + (up[4] * 2) + " HP";
            case 5 -> Math.round(Levels.critRate(up) * 100f) + "%";
            default -> "+" + Math.round(Levels.mastery(up) * 100f) + "%";
        };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int accent = ClientKit.element >= 0 ? (0xFF000000 | Element.VALUES[ClientKit.element].color) : 0xFFB8BDD1;

        g.fill(px - 1, py - 1, px + PW + 1, py + PH + 1, accent);
        g.fillGradient(px, py, px + PW, py + PH, 0xF0181A26, 0xF00E0F16);

        // 헤더
        g.drawString(font, this.title, px + 16, py + 12, accent, true);
        String lv = "Lv." + ClientKit.level;
        g.drawString(font, lv, px + PW - 16 - font.width(lv), py + 12, 0xFFFFFFFF, true);

        float frac = ClientKit.xpNeed > 0 ? Math.min(1f, ClientKit.xp / ClientKit.xpNeed) : 0f;
        int bx = px + 16, bw = PW - 32, by = py + 32;
        g.fill(bx, by, bx + bw, by + 6, 0xFF1B1D28);
        g.fill(bx, by, bx + (int) (bw * frac), by + 6, accent);
        g.drawString(font, (int) ClientKit.xp + " / " + (int) ClientKit.xpNeed, bx, by + 10, 0xFFB8BDD1, false);

        Component pts = Component.translatable("gui.elementalgaze.points", ClientKit.points);
        g.drawString(font, pts, px + PW - 16 - font.width(pts), by + 10, ClientKit.points > 0 ? 0xFFFFD75E : 0xFF8A8FA8, true);

        // 구획
        g.drawString(font, Component.translatable("gui.elementalgaze.section.skills"), px + 16, py + 66, 0xFF8A8FA8, false);
        g.fill(px + 16, py + 77, px + PW - 16, py + 78, 0x33FFFFFF);
        g.drawString(font, Component.translatable("gui.elementalgaze.section.stats"), px + 16, rowY(3) - 20, 0xFF8A8FA8, false);
        g.fill(px + 16, rowY(3) - 9, px + PW - 16, rowY(3) - 8, 0x33FFFFFF);

        for (int i = 0; i < Levels.SLOTS; i++) {
            int y = rowY(i);
            g.drawString(font, Component.translatable("gui.elementalgaze.row." + NAMES[i]), px + 16, y, 0xFFE6E8F2, false);
            String v = value(i);
            g.drawString(font, v, px + PW - 46 - font.width(v), y, accent, false);
            plus[i].active = ClientKit.points > 0 && ClientKit.up[i] < Levels.cap(i);
        }
        super.render(g, mx, my, pt);
    }
}
