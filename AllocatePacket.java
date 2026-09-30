package com.example.elementalgaze;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** 포인트 투자 요청. 슬롯 번호만 보내고 검증은 전부 서버가 한다. */
public final class AllocatePacket {
    public final byte slot;

    public AllocatePacket(int slot) {
        this.slot = (byte) slot;
    }

    public static void encode(AllocatePacket m, FriendlyByteBuf b) {
        b.writeByte(m.slot);
    }

    public static AllocatePacket decode(FriendlyByteBuf b) {
        return new AllocatePacket(b.readByte());
    }

    public static void handle(AllocatePacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> {
            ServerPlayer p = ctx.getSender();
            if (p != null) GazeManager.allocate(p, m.slot);
        });
        ctx.setPacketHandled(true);
    }
}
