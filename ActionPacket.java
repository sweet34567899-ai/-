package com.example.elementalgaze;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** action: 0=전투모드 토글, 1=일반공격, 2=E, 3=Q. 1바이트. */
public final class ActionPacket {
    public final byte action;

    public ActionPacket(int action) {
        this.action = (byte) action;
    }

    public static void encode(ActionPacket m, FriendlyByteBuf b) {
        b.writeByte(m.action);
    }

    public static ActionPacket decode(FriendlyByteBuf b) {
        return new ActionPacket(b.readByte());
    }

    public static void handle(ActionPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null) GazeManager.handleAction(p, m.action);
        });
        ctx.setPacketHandled(true);
    }
}
