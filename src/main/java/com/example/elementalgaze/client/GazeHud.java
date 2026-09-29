package com.example.elementalgaze.client;

import com.example.elementalgaze.ClientKit;
import com.example.elementalgaze.Element;
import com.example.elementalgaze.GazeCharacter;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

public final class GazeHud {
    private GazeHud() {}

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.level == null) return;
        Font f = mc.font;
        int x = 6, y = h - 64;
        long now = mc.level.getGameTime();

        if (ClientKit.stage < 2) {
            int pct = ClientKit.energyMax > 0 ? (int) (ClientKit.energy / ClientKit.energyMax * 100f) : 0;
            String key = ClientKit.stage == 0 ? "hud.elementalgaze.gaze" : "hud.elementalgaze.element";
            g.drawString(f, Component.translatable(key, pct), x, y + 40, 0xFFD8D8D8, true);
            return;
        }

        Element el = ClientKit.element >= 0 ? Element.VALUES[ClientKit.element] : Element.PYRO;
        int col = 0xFF000000 | el.color;
        MutableComponent title = Component.translatable("element.elementalgaze." + el.id).append(" · ")
                .append(Component.translatable(GazeCharacter.nameKey(ClientKit.characterId)));
        if (ClientKit.archon) title.append(" ★");
        g.drawString(f, title, x, y, col, true);

        long sLeft = ClientKit.skillAt - now;
        Component skill = sLeft <= 0
                ? Component.translatable("hud.elementalgaze.ready")
                : Component.literal(String.format(Locale.ROOT, "%.1fs", sLeft / 20.0));
        g.drawString(f, Component.literal("[").append(Keys.SKILL.getTranslatedKeyMessage()).append("] E ").append(skill),
                x, y + 12, sLeft <= 0 ? 0xFF9BE37A : 0xFFAAAAAA, true);

        long bLeft = ClientKit.burstAt - now;
        boolean full = ClientKit.burst >= ClientKit.burstCost && bLeft <= 0;
        Component burst = Component.literal(String.format(Locale.ROOT, "%d/%d", (int) ClientKit.burst, (int) ClientKit.burstCost));
        g.drawString(f, Component.literal("[").append(Keys.BURST.getTranslatedKeyMessage()).append("] Q ").append(burst),
                x, y + 24, full ? 0xFFFFD75E : 0xFFAAAAAA, true);

        g.drawString(f, Component.literal("[").append(Keys.COMBAT.getTranslatedKeyMessage()).append("] ")
                        .append(Component.translatable(ClientKit.combat ? "hud.elementalgaze.combat_on" : "hud.elementalgaze.combat_off")),
                x, y + 36, ClientKit.combat ? 0xFFFF7A7A : 0xFFAAAAAA, true);
    }
}
