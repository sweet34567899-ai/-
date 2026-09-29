package com.example.elementalgaze;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 서버 전용. 스킬 실행은 발동 순간에 1회만 범위 탐색한다(지속 틱 없음). */
public final class SkillExecutor {
    private SkillExecutor() {}

    private static boolean isEnemy(LivingEntity e) {
        return e instanceof Enemy && e.isAlive();
    }

    /** 기준 공격력 = 플레이어 공격력 속성(들고 있는 무기의 수치 반영). 무기 종류는 무관. */
    private static float base(ServerPlayer p) {
        return Math.max((float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), Config.MIN_BASE_ATK.get().floatValue());
    }

    // ---------------- 일반공격 ----------------
    public static void normal(ServerPlayer p, PlayerKit kit, GazeCharacter c) {
        SkillDef d = c.normal;
        long now = p.level().getGameTime();
        if (now < kit.nextNormalAt) return;
        kit.nextNormalAt = now + Math.max(2, d.interval);
        boolean ranged = "ranged".equals(d.type);
        LivingEntity t = pickTarget(p, d.range, ranged ? 0.97 : 0.75);
        p.swing(InteractionHand.MAIN_HAND, true);
        if (ranged) trail(p, t, d.range, c.element);
        if (t != null) hit(p, t, c.element, base(p) * d.mult);
    }

    // ---------------- 원소전투스킬(E) ----------------
    public static void skill(ServerPlayer p, PlayerKit kit, GazeCharacter c) {
        long now = p.level().getGameTime();
        if (now < kit.skillReadyAt) {
            p.displayClientMessage(Component.translatable("msg.elementalgaze.cooldown"), true);
            return;
        }
        SkillDef d = c.skill;
        perform(p, c, d);
        kit.skillReadyAt = now + d.cooldown;
        kit.burstEnergy = Math.min(c.burst.energyCost, kit.burstEnergy + d.energyGain);
        kit.dirty = true;
        p.level().playSound(null, p.blockPosition(), SoundEvents.ENDER_EYE_LAUNCH, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    // ---------------- 원소폭발(Q) ----------------
    public static void burst(ServerPlayer p, PlayerKit kit, GazeCharacter c) {
        long now = p.level().getGameTime();
        if (now < kit.burstReadyAt) {
            p.displayClientMessage(Component.translatable("msg.elementalgaze.cooldown"), true);
            return;
        }
        if (kit.burstEnergy < c.burst.energyCost) {
            p.displayClientMessage(Component.translatable("msg.elementalgaze.no_energy"), true);
            return;
        }
        perform(p, c, c.burst);
        kit.burstEnergy = 0f;
        kit.burstReadyAt = now + c.burst.cooldown;
        kit.dirty = true;
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1.0f, 1.4f);
    }

    private static void perform(ServerPlayer p, GazeCharacter c, SkillDef d) {
        ServerLevel lvl = p.serverLevel();
        float dmg = base(p) * d.mult;
        Vec3 center;
        double radius = d.radius;
        if ("dash".equals(d.type)) {
            Vec3 look = p.getLookAngle();
            Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
            p.setDeltaMovement(dir.x * d.range * 0.25, 0.15, dir.z * d.range * 0.25);
            p.hurtMarked = true;
            center = p.position().add(dir.scale(d.range * 0.5));
            radius = Math.min(6.0, d.range * 0.5 + d.radius);
        } else if (d.range > 0) {
            LivingEntity t = pickTarget(p, d.range, 0.9);
            center = t != null ? t.position() : p.getEyePosition().add(p.getLookAngle().scale(d.range * 0.6));
        } else {
            center = p.position();
        }
        int max = Config.MAX_TARGETS.get();
        int n = 0;
        for (LivingEntity e : enemiesAround(lvl, center, radius)) {
            hit(p, e, c.element, dmg);
            if (++n >= max) break;
        }
        lvl.sendParticles(Fx.particle(c.element), center.x, center.y + 1.0, center.z,
                24 + (int) radius * 4, radius * 0.4, 0.6, radius * 0.4, 0.05);
    }

    // ---------------- 공통 ----------------
    private static void hit(ServerPlayer p, LivingEntity t, Element el, float dmg) {
        float out = Reactions.apply(p, t, el, dmg);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), out);
        ((ServerLevel) t.level()).sendParticles(Fx.particle(el), t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(),
                6, 0.3, 0.3, 0.3, 0.05);
    }

    private static void trail(ServerPlayer p, @Nullable LivingEntity t, double range, Element el) {
        ServerLevel lvl = p.serverLevel();
        Vec3 eye = p.getEyePosition().add(0, -0.2, 0);
        Vec3 look = p.getLookAngle();
        double len = t != null ? Math.min(range, t.position().distanceTo(eye)) : range;
        for (double s = 1.5; s < len; s += 1.5) {
            lvl.sendParticles(Fx.particle(el), eye.x + look.x * s, eye.y + look.y * s, eye.z + look.z * s, 1, 0, 0, 0, 0);
        }
    }

    private static List<LivingEntity> enemiesAround(ServerLevel lvl, Vec3 c, double r) {
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        return lvl.getEntitiesOfClass(LivingEntity.class, box,
                e -> isEnemy(e) && e.distanceToSqr(c) <= (r + e.getBbWidth()) * (r + e.getBbWidth()));
    }

    @Nullable
    private static LivingEntity pickTarget(ServerPlayer p, double range, double minDot) {
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        AABB box = p.getBoundingBox().expandTowards(look.scale(range)).inflate(2.0);
        LivingEntity best = null;
        double bestDot = minDot;
        for (LivingEntity e : p.level().getEntitiesOfClass(LivingEntity.class, box, SkillExecutor::isEnemy)) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double dist = to.length();
            if (dist > range + e.getBbWidth()) continue;
            double dot = to.normalize().dot(look);
            if (dot > bestDot && p.hasLineOfSight(e)) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }
}
