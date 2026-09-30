package com.example.elementalgaze;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 서버 전용 스킬 실행기. 지속/다단히트 스킬은 엔티티를 만들지 않고 Scheduler 작업으로 처리한다.
 * 같은 종류의 지속 스킬은 재시전 시 토큰이 바뀌어 이전 작업이 스스로 종료된다.
 */
public final class SkillExecutor {
    private SkillExecutor() {}

    private static final Map<String, Integer> TOKENS = new HashMap<>();

    public static void clearTokens() { TOKENS.clear(); }

    private static int newToken(UUID id, String key) { return TOKENS.merge(id + key, 1, Integer::sum); }

    private static boolean tokenAlive(UUID id, String key, int token) {
        Integer c = TOKENS.get(id + key);
        return c != null && c == token;
    }

    /** 스케줄된 작업에서 플레이어를 다시 찾기 위한 컨텍스트 */
    private record Ctx(UUID id, MinecraftServer server, GazeCharacter c, SkillDef d, int slot) {
        @Nullable
        ServerPlayer player() {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            return (p != null && p.isAlive()) ? p : null;
        }

        Element el() { return c.element; }
    }

    // ================= 진입점 =================
    public static void normal(ServerPlayer p, PlayerKit kit, GazeCharacter c) {
        SkillDef d = c.normal;
        long now = p.level().getGameTime();
        if (now < kit.nextNormalAt) return;
        kit.nextNormalAt = now + Math.max(2, d.interval);
        boolean ranged = "ranged".equals(d.type);
        LivingEntity t = pickTarget(p, d.range, ranged ? 0.97 : 0.75);
        p.swing(InteractionHand.MAIN_HAND, true);
        ServerLevel l = p.serverLevel();
        if (ranged) {
            Vec3 look = p.getLookAngle();
            Vec3 from = p.getEyePosition().add(look.scale(0.8)).add(0, -0.2, 0);
            Vec3 end = t != null ? t.getBoundingBox().getCenter() : p.getEyePosition().add(look.scale(d.range));
            Fx.line(l, c.element, from, end);
        } else {
            Fx.slash(l, c.element, p.getEyePosition(), p.getLookAngle());
        }
        if (t != null) hit(p, t, c.element, dmg(p, d, Levels.NORMAL));
    }

    public static void skill(ServerPlayer p, PlayerKit kit, GazeCharacter c) {
        long now = p.level().getGameTime();
        if (now < kit.skillReadyAt) {
            p.displayClientMessage(Component.translatable("msg.elementalgaze.cooldown"), true);
            return;
        }
        perform(p, c, c.skill, Levels.SKILL);
        kit.skillReadyAt = now + c.skill.cooldown;
        kit.burstEnergy = Math.min(c.burst.energyCost, kit.burstEnergy + c.skill.energyGain);
        kit.dirty = true;
    }

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
        perform(p, c, c.burst, Levels.BURST);
        kit.burstEnergy = 0f;
        kit.burstReadyAt = now + c.burst.cooldown;
        kit.dirty = true;
    }

    private static void perform(ServerPlayer p, GazeCharacter c, SkillDef d, int slot) {
        Ctx cx = new Ctx(p.getUUID(), p.server, c, d, slot);
        Fx.sound(p.serverLevel(), c.element, p.getX(), p.getY(), p.getZ(), slot == Levels.BURST);
        switch (d.type) {
            case "dash" -> dash(cx);
            case "cone" -> cone(cx);
            case "strikes" -> strikes(cx);
            case "field" -> field(cx);
            case "orbit" -> orbit(cx);
            case "summon" -> summon(cx);
            case "barrier" -> barrier(cx);
            case "boomerang" -> boomerang(cx);
            case "delayed_burst" -> delayedBurst(cx);
            default -> aoe(cx);
        }
    }

    // ================= 스킬 동작 =================

    /** aoe: 대상 지점(range>0) 또는 자신 주변(range=0)에 즉발 범위 피해. radius */
    private static void aoe(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        ServerLevel l = p.serverLevel();
        SkillDef d = cx.d();
        Element el = cx.el();
        Vec3 center = pickCenter(p, d);
        Fx.burst(l, el, center.x, center.y, center.z, d.radius);
        float dm = dmg(p, d, cx.slot());
        int n = 0, max = maxTargets();
        for (LivingEntity e : enemiesAround(l, center, d.radius)) {
            hit(p, e, el, dm);
            if (++n >= max) break;
        }
    }

    /** dash: 바라보는 방향으로 돌진하며 경로 주변에 피해. range=거리, radius */
    private static void dash(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        ServerLevel l = p.serverLevel();
        SkillDef d = cx.d();
        Element el = cx.el();
        Vec3 look = p.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z).normalize();
        Vec3 start = p.position();
        p.setDeltaMovement(dir.x * d.range * 0.25, 0.15, dir.z * d.range * 0.25);
        p.hurtMarked = true;
        Vec3 mid = start.add(dir.scale(d.range * 0.5));
        double radius = Math.min(6.0, d.range * 0.5 + d.radius);
        Fx.line(l, el, start.add(0, 1, 0), start.add(dir.scale(d.range)).add(0, 1, 0));
        Fx.burst(l, el, mid.x, mid.y, mid.z, radius * 0.7);
        float dm = dmg(p, d, cx.slot());
        int n = 0, max = maxTargets();
        for (LivingEntity e : enemiesAround(l, mid, radius)) {
            hit(p, e, el, dm);
            if (++n >= max) break;
        }
    }

    /**
     * cone: 전방 부채꼴. waves=반복 횟수, tick=간격, range, cone=각도(내적 하한),
     * progressive=true 이면 파동이 앞으로 퍼지며 구간별로 한 번씩만 타격.
     */
    private static void cone(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        SkillDef d = cx.d();
        Element el = cx.el();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        final Vec3 dir = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        final Vec3 origin = p.position().add(0, 1.0, 0);
        final int waves = Math.max(1, d.waves);
        final boolean prog = d.progressive;
        for (int w = 0; w < waves; w++) {
            final int idx = w;
            Runnable step = () -> {
                ServerPlayer sp = cx.player();
                if (sp == null) return;
                ServerLevel l = sp.serverLevel();
                double lo = prog ? d.range * idx / waves : 0.0;
                double hi = prog ? d.range * (idx + 1) / waves : d.range;
                for (double s = Math.max(1.0, lo); s <= hi; s += 1.0) {
                    Fx.stream(l, el, origin.add(dir.scale(s)), 0.4 + s * 0.28);
                }
                float dm = dmg(sp, d, cx.slot());
                int n = 0, max = maxTargets();
                Vec3 mid = origin.add(dir.scale(d.range * 0.5));
                for (LivingEntity e : enemiesAround(l, mid, d.range * 0.5 + 1.5)) {
                    Vec3 to = e.position().subtract(origin);
                    double dist = to.length();
                    if (dist < lo || dist > hi + e.getBbWidth()) continue;
                    if (dist > 0.5 && to.multiply(1, 0, 1).normalize().dot(dir) < d.cone) continue;
                    hit(sp, e, el, dm);
                    if (++n >= max) break;
                }
            };
            if (w == 0) step.run(); else Scheduler.later(w * Math.max(1, d.tick), step);
        }
    }

    /**
     * strikes: 여러 번 내리꽂는 일격. waves=횟수, tick=간격, aoe=일격 반경.
     * style="target": 대상 지점에 집중. 그 외: radius 안의 적을 무작위로 골라 타격(follow=true면 시전자 기준).
     */
    private static void strikes(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        SkillDef d = cx.d();
        final Vec3 center = pickCenter(p, d);
        int n = Math.max(1, d.waves);
        for (int i = 0; i < n; i++) {
            final int idx = i;
            Runnable r = () -> {
                ServerPlayer sp = cx.player();
                if (sp == null) return;
                ServerLevel l = sp.serverLevel();
                Vec3 pos = center;
                if (!"target".equals(d.style)) {
                    Vec3 origin = d.follow ? sp.position() : center;
                    List<LivingEntity> es = enemiesAround(l, origin, d.radius);
                    if (!es.isEmpty()) {
                        pos = es.get(sp.getRandom().nextInt(es.size())).position();
                    } else {
                        pos = origin.add((sp.getRandom().nextDouble() - 0.5) * d.radius, 0,
                                (sp.getRandom().nextDouble() - 0.5) * d.radius);
                    }
                }
                strikeAt(sp, cx, pos, idx);
            };
            if (i == 0) r.run(); else Scheduler.later(i * Math.max(1, d.tick), r);
        }
    }

    private static void strikeAt(ServerPlayer p, Ctx cx, Vec3 pos, int idx) {
        ServerLevel l = p.serverLevel();
        SkillDef d = cx.d();
        Element el = cx.el();
        Fx.strike(l, el, pos.x, pos.y, pos.z, d.aoe, idx < 4);
        float dm = dmg(p, d, cx.slot());
        int n = 0, max = maxTargets();
        for (LivingEntity e : enemiesAround(l, pos, d.aoe)) {
            hit(p, e, el, dm);
            if (++n >= max) break;
        }
    }

    /**
     * field: 지속 필드. duration/tick 마다 pulse, radius, hits=한 번에 맞는 최대 수, pull=끌어당김 세기,
     * follow=시전자를 따라다님. style: "vortex"(소용돌이), "columns"(적마다 물줄기), 기본은 링.
     */
    private static void field(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        SkillDef d = cx.d();
        final Vec3 fixed = pickCenter(p, d);
        int tick = Math.max(1, d.tick);
        int pulses = Math.max(1, d.duration / tick);
        for (int i = 0; i < pulses; i++) {
            final int idx = i;
            Runnable r = () -> {
                ServerPlayer sp = cx.player();
                if (sp == null) return;
                fieldPulse(sp, cx, d.follow ? sp.position() : fixed, idx);
            };
            if (i == 0) r.run(); else Scheduler.later(i * tick, r);
        }
    }

    private static void fieldPulse(ServerPlayer p, Ctx cx, Vec3 c, int idx) {
        ServerLevel l = p.serverLevel();
        SkillDef d = cx.d();
        Element el = cx.el();
        List<LivingEntity> es = enemiesAround(l, c, d.radius);
        boolean columns = "columns".equals(d.style);
        if ("vortex".equals(d.style)) {
            Fx.spiral(l, el, c.x, c.y, c.z, d.radius * 0.8, 3.0, 2.0, 14, idx * 0.9);
            Fx.ring(l, el, c.x, c.y + 0.2, c.z, d.radius, 20);
        } else {
            Fx.ring(l, el, c.x, c.y + 0.2, c.z, d.radius, 20);
        }
        if (d.pull > 0f) {
            for (LivingEntity e : es) pull(e, c, d.pull);
        }
        float dm = dmg(p, d, cx.slot());
        int n = 0, max = Math.min(d.hits, maxTargets());
        for (LivingEntity e : es) {
            if (n >= max) break;
            if (columns) Fx.strike(l, el, e.getX(), e.getY(), e.getZ(), 1.5, false);
            hit(p, e, el, dm);
            n++;
        }
    }

    /** orbit: 시전자 주위를 도는 구체. arms=개수, radius=궤도 반경, tick=연출 간격, interval=피해 간격. */
    private static void orbit(Ctx cx) {
        ServerPlayer p0 = cx.player();
        if (p0 == null) return;
        SkillDef d = cx.d();
        Element el = cx.el();
        final String key = ":orbit" + cx.slot();
        final int token = newToken(cx.id(), key);
        int tick = Math.max(1, d.tick);
        int pulses = Math.max(1, d.duration / tick);
        Fx.burst(p0.serverLevel(), el, p0.getX(), p0.getY(), p0.getZ(), 2.5);
        for (int i = 0; i < pulses; i++) {
            final int idx = i;
            Runnable r = () -> {
                if (!tokenAlive(cx.id(), key, token)) return;
                ServerPlayer sp = cx.player();
                if (sp == null) return;
                ServerLevel l = sp.serverLevel();
                Vec3 c = sp.position().add(0, 1.0, 0);
                double base = idx * 0.55;
                for (int a = 0; a < d.arms; a++) {
                    double ang = base + a * (Math.PI * 2 / d.arms);
                    Fx.orbitPoint(l, el, c.x + Math.cos(ang) * d.radius, c.y + Math.sin(idx * 0.4 + a) * 0.35,
                            c.z + Math.sin(ang) * d.radius);
                }
                if ((idx * tick) % Math.max(1, d.interval) == 0) {
                    float dm = dmg(sp, d, cx.slot());
                    int n = 0, max = maxTargets();
                    for (LivingEntity e : enemiesAround(l, sp.position(), d.radius + 0.8)) {
                        hit(sp, e, el, dm);
                        if (++n >= max) break;
                    }
                }
            };
            if (i == 0) r.run(); else Scheduler.later(i * tick, r);
        }
    }

    /** summon: 어깨 위에 떠서 가까운 적을 공격하는 소환체(파티클). range=공격 사거리, interval=공격 간격. 플레이어당 1개. */
    private static void summon(Ctx cx) {
        ServerPlayer p0 = cx.player();
        if (p0 == null) return;
        SkillDef d = cx.d();
        Element el = cx.el();
        final String key = ":summon";
        final int token = newToken(cx.id(), key);
        int tick = Math.max(1, d.tick);
        int pulses = Math.max(1, d.duration / tick);
        for (int i = 0; i < pulses; i++) {
            final int idx = i;
            Runnable r = () -> {
                if (!tokenAlive(cx.id(), key, token)) return;
                ServerPlayer sp = cx.player();
                if (sp == null) return;
                ServerLevel l = sp.serverLevel();
                Vec3 look = sp.getLookAngle();
                Vec3 right = new Vec3(-look.z, 0, look.x);
                right = right.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : right.normalize();
                Vec3 oz = sp.position().add(0, 2.3 + Math.sin(idx * 0.6) * 0.15, 0).add(right.scale(0.9));
                Fx.summon(l, el, oz);
                if ((idx * tick) % Math.max(1, d.interval) == 0) {
                    LivingEntity t = nearestEnemy(l, oz, d.range);
                    if (t != null) {
                        Fx.line(l, el, oz, t.getBoundingBox().getCenter());
                        hit(sp, t, el, dmg(sp, d, cx.slot()));
                        Fx.sound(l, el, oz.x, oz.y, oz.z, false);
                    }
                }
            };
            if (i == 0) r.run(); else Scheduler.later(i * tick, r);
        }
    }

    /** barrier: 전방에 벽을 세우고 흡수 체력+피해 감소를 부여. aoe=설치 시 타격 반경, duration. */
    private static void barrier(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        ServerLevel l = p.serverLevel();
        SkillDef d = cx.d();
        Element el = cx.el();
        PlayerKit k = PlayerKit.get(p);
        int lvl = k != null ? k.up[Levels.SKILL] : 0;
        int dur = Math.max(20, d.duration);
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, dur, 1 + Math.min(2, lvl / 3)));
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 0));
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        Vec3 dir = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();
        final Vec3 wall = p.position().add(dir.scale(2.6));
        final Vec3 side = new Vec3(-dir.z, 0, dir.x);
        final String key = ":barrier";
        final int token = newToken(cx.id(), key);
        Fx.burst(l, el, wall.x, wall.y, wall.z, d.aoe);
        Fx.wall(l, el, wall, side);
        float dm = dmg(p, d, cx.slot());
        int n = 0, max = maxTargets();
        for (LivingEntity e : enemiesAround(l, wall, d.aoe)) {
            hit(p, e, el, dm);
            if (++n >= max) break;
        }
        int tick = Math.max(1, d.tick);
        int pulses = Math.max(1, dur / tick);
        for (int i = 1; i < pulses; i++) {
            Scheduler.later(i * tick, () -> {
                if (!tokenAlive(cx.id(), key, token)) return;
                ServerPlayer sp = cx.player();
                if (sp == null) return;
                Fx.wall(sp.serverLevel(), el, wall, side);
            });
        }
    }

    /** boomerang: 전방 직선으로 나갔다가 돌아오며 경로의 적을 두 번 타격. range=거리, radius=경로 폭 */
    private static void boomerang(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        final Vec3 a = p.getEyePosition().add(0, -0.3, 0);
        final Vec3 b = a.add(p.getLookAngle().scale(cx.d().range));
        sweep(p, cx, a, b);
        Scheduler.later(7, () -> {
            ServerPlayer sp = cx.player();
            if (sp == null) return;
            sweep(sp, cx, b, sp.getEyePosition().add(0, -0.3, 0));
        });
    }

    private static void sweep(ServerPlayer sp, Ctx cx, Vec3 from, Vec3 to) {
        ServerLevel l = sp.serverLevel();
        SkillDef d = cx.d();
        Element el = cx.el();
        Fx.line(l, el, from, to);
        Fx.burst(l, el, to.x, to.y - 0.5, to.z, 1.2);
        Vec3 mid = from.add(to).scale(0.5);
        double half = from.distanceTo(to) / 2 + 2.0;
        float dm = dmg(sp, d, cx.slot());
        int n = 0, max = maxTargets();
        for (LivingEntity e : enemiesAround(l, mid, half)) {
            if (distToSegment(e.getBoundingBox().getCenter(), from, to) > d.radius) continue;
            hit(sp, e, el, dm);
            if (++n >= max) break;
        }
    }

    /** delayed_burst: 대상 지점에 표식 후 delay 틱 뒤 대폭발(radius), 이후 duration/tick 동안 잔류 피해(aoe). */
    private static void delayedBurst(Ctx cx) {
        ServerPlayer p = cx.player();
        if (p == null) return;
        final ServerLevel l = p.serverLevel();
        SkillDef d = cx.d();
        Element el = cx.el();
        final Vec3 center = pickCenter(p, d);
        final int delay = Math.max(5, d.delay);
        Fx.column(l, el, center.x, center.y, center.z, 3.0);
        for (int t = 5; t < delay; t += 5) {
            final int tt = t;
            Scheduler.later(t, () -> {
                Fx.ring(l, el, center.x, center.y + 0.2, center.z, Math.max(0.8, d.radius * (1.0 - (double) tt / delay)), 16);
                Fx.orbitPoint(l, el, center.x, center.y + 1.2, center.z);
            });
        }
        Scheduler.later(delay, () -> {
            ServerPlayer sp = cx.player();
            if (sp == null) return;
            Fx.strike(l, el, center.x, center.y, center.z, d.radius, true);
            Fx.sound(l, el, center.x, center.y, center.z, true);
            float dm = dmg(sp, d, cx.slot());
            int n = 0, max = maxTargets();
            for (LivingEntity e : enemiesAround(l, center, d.radius)) {
                hit(sp, e, el, dm);
                if (++n >= max) break;
            }
        });
        int tick = Math.max(1, d.tick);
        int pulses = d.duration / tick;
        for (int i = 1; i <= pulses; i++) {
            Scheduler.later(delay + i * tick, () -> {
                ServerPlayer sp = cx.player();
                if (sp == null) return;
                Fx.ring(l, el, center.x, center.y + 0.2, center.z, d.aoe, 16);
                Fx.orbitPoint(l, el, center.x, center.y + 0.8, center.z);
                float dm = dmg(sp, d, cx.slot()) * 0.3f;
                int n = 0, max = maxTargets();
                for (LivingEntity e : enemiesAround(l, center, d.aoe)) {
                    hit(sp, e, el, dm);
                    if (++n >= max) break;
                }
            });
        }
    }

    // ================= 공통 =================

    private static boolean isEnemy(LivingEntity e) { return e instanceof Enemy && e.isAlive(); }

    private static int maxTargets() { return Config.MAX_TARGETS.get(); }

    /** 기준 공격력 = 플레이어 공격력 속성(들고 있는 무기 수치 반영, 무기 종류는 무관). */
    private static float base(ServerPlayer p) {
        return Math.max((float) p.getAttributeValue(Attributes.ATTACK_DAMAGE), Config.MIN_BASE_ATK.get().floatValue());
    }

    /** 기준공격력 x 스킬배율 x 스킬레벨 x 공격력/레벨 보너스 */
    private static float dmg(ServerPlayer p, SkillDef d, int slot) {
        PlayerKit k = PlayerKit.get(p);
        float m = 1f, a = 1f;
        if (k != null) {
            m = Levels.skillMul(k.up, slot);
            a = Levels.atkMul(k.up, k.level);
        }
        return base(p) * d.mult * m * a;
    }

    private static void hit(ServerPlayer p, LivingEntity t, Element el, float dmg) {
        PlayerKit k = PlayerKit.get(p);
        boolean crit = false;
        float bonus = 0f;
        if (k != null) {
            bonus = Levels.mastery(k.up);
            if (p.getRandom().nextFloat() < Levels.critRate(k.up)) {
                dmg *= Levels.CRIT_DMG;
                crit = true;
            }
        }
        float out = Reactions.apply(p, t, el, dmg, bonus);
        t.invulnerableTime = 0;
        t.hurt(p.damageSources().playerAttack(p), out);
        ServerLevel l = (ServerLevel) t.level();
        double y = t.getY() + t.getBbHeight() * 0.5;
        Fx.hit(l, el, t.getX(), y, t.getZ());
        if (crit) l.sendParticles(ParticleTypes.CRIT, t.getX(), y + 0.3, t.getZ(), 8, 0.3, 0.3, 0.3, 0.3);
    }

    private static void pull(LivingEntity e, Vec3 center, float strength) {
        Vec3 v = center.subtract(e.position());
        double len = v.length();
        if (len < 0.5) return;
        Vec3 m = v.scale(strength / len);
        e.setDeltaMovement(e.getDeltaMovement().add(m.x, 0.08, m.z));
        e.hurtMarked = true;
    }

    private static Vec3 pickCenter(ServerPlayer p, SkillDef d) {
        if (d.range <= 0) return p.position();
        LivingEntity t = pickTarget(p, d.range, 0.9);
        if (t != null) return t.position();
        Vec3 pt = p.getEyePosition().add(p.getLookAngle().scale(d.range * 0.6));
        return new Vec3(pt.x, p.getY(), pt.z);
    }

    private static List<LivingEntity> enemiesAround(ServerLevel lvl, Vec3 c, double r) {
        AABB box = new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        return lvl.getEntitiesOfClass(LivingEntity.class, box,
                e -> isEnemy(e) && e.distanceToSqr(c) <= (r + e.getBbWidth()) * (r + e.getBbWidth()));
    }

    @Nullable
    private static LivingEntity nearestEnemy(ServerLevel l, Vec3 from, double range) {
        LivingEntity best = null;
        double bd = range * range;
        for (LivingEntity e : enemiesAround(l, from, range)) {
            double dd = e.distanceToSqr(from);
            if (dd < bd) {
                bd = dd;
                best = e;
            }
        }
        return best;
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1e-6 ? 0.0 : Mth.clamp(p.subtract(a).dot(ab) / len2, 0.0, 1.0);
        return p.distanceTo(a.add(ab.scale(t)));
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
