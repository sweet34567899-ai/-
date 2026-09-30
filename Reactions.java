package com.example.elementalgaze;

import java.util.Arrays;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * 프로토타입 원소 반응. 몹당 원소 1개만 부착하며, 부착은 "만료 시각"만 저장(틱 갱신 없음).
 * 반응표는 [공격원소][부착원소] 2차원 배열로 O(1) 조회.
 */
public final class Reactions {
    private enum R {
        NONE(""), VAPORIZE_2("vaporize"), VAPORIZE_15("vaporize"), MELT_2("melt"), MELT_15("melt"),
        OVERLOAD("overloaded"), ELECTROCHARGED("electrocharged"), SUPERCONDUCT("superconduct"), FROZEN("frozen");
        final String key;
        R(String key) { this.key = key; }
    }

    private static final String K_EL = "gz_el", K_EXP = "gz_exp";
    private static final int AURA_TICKS = 160;
    private static final R[][] T = new R[Element.VALUES.length][Element.VALUES.length];

    static {
        for (R[] row : T) Arrays.fill(row, R.NONE);
        set(Element.PYRO, Element.HYDRO, R.VAPORIZE_15);
        set(Element.HYDRO, Element.PYRO, R.VAPORIZE_2);
        set(Element.PYRO, Element.CRYO, R.MELT_2);
        set(Element.CRYO, Element.PYRO, R.MELT_15);
        both(Element.PYRO, Element.ELECTRO, R.OVERLOAD);
        both(Element.ELECTRO, Element.HYDRO, R.ELECTROCHARGED);
        both(Element.ELECTRO, Element.CRYO, R.SUPERCONDUCT);
        both(Element.HYDRO, Element.CRYO, R.FROZEN);
    }

    private static void set(Element atk, Element aura, R r) { T[atk.ordinal()][aura.ordinal()] = r; }
    private static void both(Element a, Element b, R r) { set(a, b, r); set(b, a, r); }

    private Reactions() {}

    /** 반응을 적용하고 최종 데미지를 반환한다. */
    public static float apply(ServerPlayer p, LivingEntity t, Element atk, float dmg, float bonus) {
        CompoundTag d = t.getPersistentData();
        long now = t.level().getGameTime();
        int auraEl = (d.contains(K_EL) && d.getLong(K_EXP) > now) ? d.getByte(K_EL) : -1;
        R r = auraEl >= 0 ? T[atk.ordinal()][auraEl] : R.NONE;

        if (r == R.NONE) {
            if (atk.aura) {
                d.putByte(K_EL, (byte) atk.ordinal());
                d.putLong(K_EXP, now + AURA_TICKS);
            }
            return dmg;
        }

        d.remove(K_EL);
        d.remove(K_EXP);
        switch (r) {
            case VAPORIZE_2, MELT_2 -> dmg *= 2f;
            case VAPORIZE_15, MELT_15 -> dmg *= 1.5f;
            case OVERLOAD -> {
                dmg *= 1.6f;
                t.knockback(0.8, p.getX() - t.getX(), p.getZ() - t.getZ());
                ((ServerLevel) t.level()).sendParticles(ParticleTypes.EXPLOSION, t.getX(), t.getY() + 1, t.getZ(), 1, 0, 0, 0, 0);
            }
            case ELECTROCHARGED -> dmg *= 1.4f;
            case SUPERCONDUCT -> {
                dmg *= 1.4f;
                t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0));
            }
            case FROZEN -> t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 6));
            default -> { }
        }
        dmg *= 1f + bonus;   // 반응 숙련 보너스
        p.displayClientMessage(Component.translatable("reaction.elementalgaze." + r.key), true);
        return dmg;
    }
}
