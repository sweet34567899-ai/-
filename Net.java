package com.example.elementalgaze;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class Net {
    private static final String PROTOCOL = "1";
    public static final SimpleChannel CH = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ElementalGaze.MODID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private Net() {}

    public static void init() {
        int id = 0;
        CH.registerMessage(id++, ActionPacket.class, ActionPacket::encode, ActionPacket::decode, ActionPacket::handle);
        CH.registerMessage(id++, SyncPacket.class, SyncPacket::encode, SyncPacket::decode, SyncPacket::handle);
        CH.registerMessage(id++, AllocatePacket.class, AllocatePacket::encode, AllocatePacket::decode, AllocatePacket::handle);
    }

    /** dirty 플래그가 켜진 경우에만 호출된다. */
    public static void sync(ServerPlayer p) {
        PlayerKit k = PlayerKit.get(p);
        if (k == null) return;
        k.dirty = false;
        CH.send(PacketDistributor.PLAYER.with(() -> p), SyncPacket.of(k));
    }
}
