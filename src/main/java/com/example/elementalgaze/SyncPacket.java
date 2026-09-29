package com.example.elementalgaze;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class SyncPacket {
    public final byte stage, element, pity;
    public final String characterId;
    public final float energy, energyMax, burst, burstCost;
    public final long skillAt, burstAt;
    public final boolean combat, archon;

    public SyncPacket(byte stage, byte element, byte pity, String characterId, float energy, float energyMax,
                      float burst, float burstCost, long skillAt, long burstAt, boolean combat, boolean archon) {
        this.stage = stage; this.element = element; this.pity = pity; this.characterId = characterId;
        this.energy = energy; this.energyMax = energyMax; this.burst = burst; this.burstCost = burstCost;
        this.skillAt = skillAt; this.burstAt = burstAt; this.combat = combat; this.archon = archon;
    }

    public static SyncPacket of(PlayerKit k) {
        GazeCharacter c = CharacterRegistry.get(k.characterId);
        float cost = c != null ? c.burst.energyCost : 0f;
        float max = (k.stage == 0 ? Config.GAZE_NEEDED.get() : Config.ELEMENT_NEEDED.get()).floatValue();
        return new SyncPacket(k.stage, k.element, k.pity, k.characterId, k.energy, max, k.burstEnergy, cost,
                k.skillReadyAt, k.burstReadyAt, k.combatMode, k.archon);
    }

    public static void encode(SyncPacket m, FriendlyByteBuf b) {
        b.writeByte(m.stage);
        b.writeByte(m.element);
        b.writeByte(m.pity);
        b.writeUtf(m.characterId);
        b.writeFloat(m.energy);
        b.writeFloat(m.energyMax);
        b.writeFloat(m.burst);
        b.writeFloat(m.burstCost);
        b.writeLong(m.skillAt);
        b.writeLong(m.burstAt);
        b.writeBoolean(m.combat);
        b.writeBoolean(m.archon);
    }

    public static SyncPacket decode(FriendlyByteBuf b) {
        return new SyncPacket(b.readByte(), b.readByte(), b.readByte(), b.readUtf(), b.readFloat(), b.readFloat(),
                b.readFloat(), b.readFloat(), b.readLong(), b.readLong(), b.readBoolean(), b.readBoolean());
    }

    public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> ClientKit.update(m));
        ctx.setPacketHandled(true);
    }
}
