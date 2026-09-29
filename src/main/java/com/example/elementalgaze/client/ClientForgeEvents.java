package com.example.elementalgaze.client;

import com.example.elementalgaze.ActionPacket;
import com.example.elementalgaze.ClientKit;
import com.example.elementalgaze.ElementalGaze;
import com.example.elementalgaze.Net;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ElementalGaze.MODID, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null) return;
        while (Keys.COMBAT.consumeClick()) Net.CH.sendToServer(new ActionPacket(0));
        while (Keys.SKILL.consumeClick()) Net.CH.sendToServer(new ActionPacket(2));
        while (Keys.BURST.consumeClick()) Net.CH.sendToServer(new ActionPacket(3));
    }

    /** 전투 모드에서는 좌클릭이 블록 파괴/바닐라 공격 대신 캐릭터 일반공격이 된다. */
    @SubscribeEvent
    public static void click(InputEvent.InteractionKeyMappingTriggered e) {
        if (e.isAttack() && ClientKit.stage == 2 && ClientKit.combat) {
            e.setCanceled(true);
            e.setSwingHand(false);
            Net.CH.sendToServer(new ActionPacket(1));
        }
    }
}
