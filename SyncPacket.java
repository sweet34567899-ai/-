package com.example.elementalgaze;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public final class SyncPacket {
    public byte stage, element, pity, rarity;
    public String characterId = "";
    public float energy, energyMax, burst, burstCost, xp, xpNeed;
    public long skillAt, burstAt;
    public int skillCd, burstCd, level, points;
    public boolean combat, archon;
    public final byte[] up = new byte[Levels.SLOTS];

    public static SyncPacket of(PlayerKit k) {
        SyncPacket m = new SyncPacket();
        GazeCharacter c = CharacterRegistry.get(k.characterId);
        m.stage = k.stage;
        m.element = k.element;
        m.pity = k.pity;
        m.characterId = k.characterId;
        m.rarity = (byte) (c != null ? c.rarity : 0);
        m.energy = k.energy;
        m.energyMax = (k.stage == 0 ? Config.GAZE_NEEDED.get() : Config.ELEMENT_NEEDED.get()).floatValue();
        m.burst = k.burstEnergy;
        m.burstCost = c != null ? c.burst.energyCost : 0f;
        m.skillAt = k.skillReadyAt;
        m.burstAt = k.burstReadyAt;
        m.skillCd = c != null ? c.skill.cooldown : 0;
        m.burstCd = c != null ? c.burst.cooldown : 0;
        m.combat = k.combatMode;
        m.archon = k.archon;
        m.level = k.level;
        m.xp = k.xp;
        m.xpNeed = Levels.xpNeed(k.level);
        m.points = k.points;
        System.arraycopy(k.up, 0, m.up, 0, Levels.SLOTS);
        return m;
    }

    public static void encode(SyncPacket m, FriendlyByteBuf b) {
        b.writeByte(m.stage);
        b.writeByte(m.element);
        b.writeByte(m.pity);
        b.writeByte(m.rarity);
        b.writeUtf(m.characterId);
        b.writeFloat(m.energy);
        b.writeFloat(m.energyMax);
        b.writeFloat(m.burst);
        b.writeFloat(m.burstCost);
        b.writeFloat(m.xp);
        b.writeFloat(m.xpNeed);
        b.writeLong(m.skillAt);
        b.writeLong(m.burstAt);
        b.writeVarInt(m.skillCd);
        b.writeVarInt(m.burstCd);
        b.writeVarInt(m.level);
        b.writeVarInt(m.points);
        b.writeBoolean(m.combat);
        b.writeBoolean(m.archon);
        for (byte u : m.up) b.writeByte(u);
    }

    public static SyncPacket decode(FriendlyByteBuf b) {
        SyncPacket m = new SyncPacket();
        m.stage = b.readByte();
        m.element = b.readByte();
        m.pity = b.readByte();
        m.rarity = b.readByte();
        m.characterId = b.readUtf();
        m.energy = b.readFloat();
        m.energyMax = b.readFloat();
        m.burst = b.readFloat();
        m.burstCost = b.readFloat();
        m.xp = b.readFloat();
        m.xpNeed = b.readFloat();
        m.skillAt = b.readLong();
        m.burstAt = b.readLong();
        m.skillCd = b.readVarInt();
        m.burstCd = b.readVarInt();
        m.level = b.readVarInt();
        m.points = b.readVarInt();
        m.combat = b.readBoolean();
        m.archon = b.readBoolean();
        for (int i = 0; i < m.up.length; i++) m.up[i] = b.readByte();
        return m;
    }

    public static void handle(SyncPacket m, Supplier<NetworkEvent.Context> s) {
        NetworkEvent.Context ctx = s.get();
        ctx.enqueueWork(() -> ClientKit.update(m));
        ctx.setPacketHandled(true);
    }
}
