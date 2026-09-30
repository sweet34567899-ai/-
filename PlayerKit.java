package com.example.elementalgaze;

import java.util.Arrays;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;

/** 플레이어별 상태. 쿨다운/피로도는 "시각"만 저장하고 틱마다 갱신하지 않는다(지연 계산). */
public final class PlayerKit {
    public static final Capability<PlayerKit> CAP = CapabilityManager.get(new CapabilityToken<PlayerKit>() {});

    public byte stage;               // 0=시선 에너지, 1=원소 에너지, 2=발현 완료
    public float energy;
    public byte element = -1;
    public String characterId = "";
    public boolean archon;
    public byte pity;                // 연속 4성 횟수
    public float burstEnergy;
    public boolean combatMode;
    public int level = 1;
    public float xp;
    public int points;
    public final byte[] up = new byte[Levels.SLOTS];
    public long skillReadyAt, burstReadyAt;
    public final float[] affinity = new float[Element.VALUES.length];
    public final float[] fatigue = new float[Activity.VALUES.length];
    public final long[] fatigueAt = new long[Activity.VALUES.length];

    // 저장하지 않는 값
    public long nextNormalAt, lastActionAt;
    public double lastX, lastZ;
    public boolean hasLast;
    public boolean dirty = true;

    @Nullable
    public static PlayerKit get(Player p) {
        return p.getCapability(CAP).resolve().orElse(null);
    }

    /** 피로도 적용 후 실제 획득량 반환. 활동이 발생할 때만 호출된다. */
    public float applyFatigue(Activity a, float base, long now) {
        int i = a.ordinal();
        float f = Math.max(0f, fatigue[i] - a.recoverPerTick * (now - fatigueAt[i]));
        float gained = base / (1f + f / a.softCap);
        fatigue[i] = f + base;
        fatigueAt[i] = now;
        return gained;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("stage", stage);
        t.putFloat("energy", energy);
        t.putByte("element", element);
        t.putString("char", characterId);
        t.putBoolean("archon", archon);
        t.putByte("pity", pity);
        t.putFloat("burst", burstEnergy);
        t.putBoolean("combat", combatMode);
        t.putInt("lvl", level);
        t.putFloat("xp", xp);
        t.putInt("pts", points);
        t.putByteArray("up", up);
        t.putLong("skillAt", skillReadyAt);
        t.putLong("burstAt", burstReadyAt);
        ListTag aff = new ListTag();
        for (float f : affinity) aff.add(FloatTag.valueOf(f));
        t.put("aff", aff);
        ListTag fat = new ListTag();
        for (float f : fatigue) fat.add(FloatTag.valueOf(f));
        t.put("fat", fat);
        t.put("fatAt", new LongArrayTag(fatigueAt));
        return t;
    }

    public void load(CompoundTag t) {
        stage = t.getByte("stage");
        energy = t.getFloat("energy");
        element = t.contains("element") ? t.getByte("element") : -1;
        characterId = t.getString("char");
        archon = t.getBoolean("archon");
        pity = t.getByte("pity");
        burstEnergy = t.getFloat("burst");
        combatMode = t.getBoolean("combat");
        level = t.contains("lvl") ? Math.max(1, t.getInt("lvl")) : 1;
        xp = t.getFloat("xp");
        points = t.getInt("pts");
        Arrays.fill(up, (byte) 0);
        byte[] u = t.getByteArray("up");
        System.arraycopy(u, 0, up, 0, Math.min(u.length, up.length));
        skillReadyAt = t.getLong("skillAt");
        burstReadyAt = t.getLong("burstAt");
        Arrays.fill(affinity, 0f);
        Arrays.fill(fatigue, 0f);
        Arrays.fill(fatigueAt, 0L);
        ListTag aff = t.getList("aff", Tag.TAG_FLOAT);
        for (int i = 0; i < Math.min(aff.size(), affinity.length); i++) affinity[i] = aff.getFloat(i);
        ListTag fat = t.getList("fat", Tag.TAG_FLOAT);
        for (int i = 0; i < Math.min(fat.size(), fatigue.length); i++) fatigue[i] = fat.getFloat(i);
        long[] at = t.getLongArray("fatAt");
        System.arraycopy(at, 0, fatigueAt, 0, Math.min(at.length, fatigueAt.length));
        dirty = true;
    }
}
