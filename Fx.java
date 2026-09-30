package com.example.elementalgaze;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 원소별 시각 연출. 전부 서버가 바닐라 파티클을 보내는 방식(클라이언트 코드 없음), 호출당 패킷 수를 작게 유지한다. */
public final class Fx {
    private Fx() {}

    private static final class GeoP {
        static final ParticleOptions BLOCK = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.RAW_GOLD_BLOCK.defaultBlockState());
        static final ParticleOptions DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GOLD_BLOCK.defaultBlockState());
    }

    private static final DustColorTransitionOptions[] DUST = new DustColorTransitionOptions[Element.VALUES.length];

    static {
        for (Element e : Element.VALUES) {
            float r = ((e.color >> 16) & 255) / 255f, g = ((e.color >> 8) & 255) / 255f, b = (e.color & 255) / 255f;
            DUST[e.ordinal()] = new DustColorTransitionOptions(new Vector3f(r, g, b),
                    new Vector3f(r * 0.4f + 0.6f, g * 0.4f + 0.6f, b * 0.4f + 0.6f), 1.1f);
        }
    }

    public static ParticleOptions dust(Element e) { return DUST[e.ordinal()]; }

    /** 원소의 대표 파티클 */
    public static ParticleOptions main(Element e) {
        return switch (e) {
            case PYRO -> ParticleTypes.FLAME;
            case HYDRO -> ParticleTypes.SPLASH;
            case ELECTRO -> ParticleTypes.ELECTRIC_SPARK;
            case CRYO -> ParticleTypes.SNOWFLAKE;
            case ANEMO -> ParticleTypes.CLOUD;
            case GEO -> GeoP.BLOCK;
            case DENDRO -> ParticleTypes.SPORE_BLOSSOM_AIR;
        };
    }

    /** 보조 파티클 */
    public static ParticleOptions sub(Element e) {
        return switch (e) {
            case PYRO -> ParticleTypes.SMALL_FLAME;
            case HYDRO -> ParticleTypes.BUBBLE_POP;
            case ELECTRO -> ParticleTypes.ENCHANTED_HIT;
            case CRYO -> ParticleTypes.END_ROD;
            case ANEMO -> ParticleTypes.POOF;
            case GEO -> GeoP.DUST;
            case DENDRO -> ParticleTypes.HAPPY_VILLAGER;
        };
    }

    /** 기존 코드 호환용 */
    public static ParticleOptions particle(Element e) { return main(e); }

    // ---------------- 기본 도형 ----------------
    public static void ring(ServerLevel l, Element e, double cx, double cy, double cz, double r, int points) {
        points = Math.max(6, Math.min(24, points));
        ParticleOptions p = dust(e);
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            l.sendParticles(p, cx + Math.cos(a) * r, cy, cz + Math.sin(a) * r, 1, 0, 0.05, 0, 0.0);
        }
    }

    public static void spiral(ServerLevel l, Element e, double cx, double cy, double cz,
                              double radius, double height, double turns, int points, double phase) {
        ParticleOptions p = dust(e), m = main(e);
        for (int i = 0; i < points; i++) {
            double t = (double) i / points;
            double a = phase + t * turns * Math.PI * 2;
            double rr = radius * (0.35 + 0.65 * t);
            l.sendParticles(i % 3 == 0 ? m : p, cx + Math.cos(a) * rr, cy + t * height, cz + Math.sin(a) * rr,
                    1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    public static void column(ServerLevel l, Element e, double x, double y, double z, double h) {
        ParticleOptions m = main(e);
        for (double yy = 0; yy <= h; yy += 0.75) l.sendParticles(m, x, y + yy, z, 2, 0.15, 0.05, 0.15, 0.01);
    }

    /** 두 점을 잇는 빔. 번개는 지그재그. */
    public static void line(ServerLevel l, Element e, Vec3 a, Vec3 b) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 0.1) return;
        Vec3 step = d.scale(1.0 / len);
        RandomSource r = l.getRandom();
        boolean jag = e == Element.ELECTRO;
        int i = 0;
        for (double s = 0; s <= len; s += 1.5, i++) {
            Vec3 c = a.add(step.scale(s));
            double j = jag ? (r.nextDouble() - 0.5) * 0.6 : 0.0;
            ParticleOptions p = (i % 2 == 0) ? dust(e) : main(e);
            l.sendParticles(p, c.x + j, c.y + j, c.z + j, 1, 0.03, 0.03, 0.03, 0.0);
        }
    }

    public static void lightning(ServerLevel l, double x, double y, double z) {
        LightningBolt b = EntityType.LIGHTNING_BOLT.create(l);
        if (b != null) {
            b.moveTo(x, y, z);
            b.setVisualOnly(true);
            l.addFreshEntity(b);
        }
    }

    // ---------------- 조합 연출 ----------------
    /** 원소별 개성이 있는 폭발. */
    public static void burst(ServerLevel l, Element e, double x, double y, double z, double r) {
        r = Math.max(1.0, r);
        l.sendParticles(dust(e), x, y + 0.7, z, 24 + (int) (r * 5), r * 0.45, 0.45, r * 0.45, 0.0);
        ring(l, e, x, y + 0.15, z, r, (int) (r * 4));
        switch (e) {
            case PYRO -> {
                l.sendParticles(ParticleTypes.FLAME, x, y + 0.3, z, 30, r * 0.45, 0.25, r * 0.45, 0.08);
                l.sendParticles(ParticleTypes.LAVA, x, y + 0.6, z, 6, r * 0.3, 0.2, r * 0.3, 0.0);
                l.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1.0, z, 6, r * 0.3, 0.4, r * 0.3, 0.02);
            }
            case HYDRO -> {
                l.sendParticles(ParticleTypes.SPLASH, x, y + 0.4, z, 40, r * 0.45, 0.3, r * 0.45, 0.2);
                l.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.6, z, 16, r * 0.4, 0.4, r * 0.4, 0.05);
                l.sendParticles(ParticleTypes.FALLING_WATER, x, y + 2.5, z, 20, r * 0.4, 0.3, r * 0.4, 0.0);
            }
            case ELECTRO -> {
                l.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y + 0.8, z, 40, r * 0.4, 0.5, r * 0.4, 0.35);
                l.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y + 1.0, z, 12, r * 0.3, 0.5, r * 0.3, 0.2);
            }
            case CRYO -> {
                l.sendParticles(ParticleTypes.SNOWFLAKE, x, y + 0.8, z, 40, r * 0.45, 0.5, r * 0.45, 0.06);
                l.sendParticles(ParticleTypes.END_ROD, x, y + 0.5, z, 14, r * 0.3, 0.2, r * 0.3, 0.12);
                l.sendParticles(ParticleTypes.ITEM_SNOWBALL, x, y + 0.5, z, 12, r * 0.4, 0.3, r * 0.4, 0.05);
            }
            case ANEMO -> {
                l.sendParticles(ParticleTypes.CLOUD, x, y + 0.5, z, 30, r * 0.4, 0.3, r * 0.4, 0.12);
                l.sendParticles(ParticleTypes.POOF, x, y + 0.6, z, 14, r * 0.4, 0.3, r * 0.4, 0.05);
                spiral(l, e, x, y, z, r * 0.8, 2.5, 2.0, 14, 0.0);
            }
            case GEO -> {
                l.sendParticles(GeoP.BLOCK, x, y + 0.3, z, 36, r * 0.45, 0.2, r * 0.45, 0.15);
                l.sendParticles(GeoP.DUST, x, y + 2.0, z, 16, r * 0.4, 0.5, r * 0.4, 0.0);
                l.sendParticles(ParticleTypes.CRIT, x, y + 0.8, z, 16, r * 0.4, 0.4, r * 0.4, 0.3);
            }
            case DENDRO -> {
                l.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y + 0.8, z, 36, r * 0.45, 0.5, r * 0.45, 0.02);
                l.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 0.6, z, 14, r * 0.4, 0.3, r * 0.4, 0.05);
                l.sendParticles(ParticleTypes.COMPOSTER, x, y + 0.5, z, 14, r * 0.4, 0.3, r * 0.4, 0.05);
            }
        }
    }

    /** 하늘에서 내리꽂는 일격. 낙뢰/보석/물줄기 등이 원소에 따라 달라진다. */
    public static void strike(ServerLevel l, Element e, double x, double y, double z, double r, boolean bolt) {
        column(l, e, x, y, z, 5.0);
        burst(l, e, x, y, z, r);
        if (bolt && e == Element.ELECTRO) lightning(l, x, y, z);
        if (e == Element.GEO) l.sendParticles(GeoP.DUST, x, y + 4, z, 14, 0.3, 1.6, 0.3, 0.0);
        if (e == Element.HYDRO) l.sendParticles(ParticleTypes.FALLING_WATER, x, y + 4, z, 16, 0.3, 1.6, 0.3, 0.0);
        sound(l, e, x, y, z, false);
    }

    public static void hit(ServerLevel l, Element e, double x, double y, double z) {
        l.sendParticles(main(e), x, y, z, 5, 0.25, 0.25, 0.25, 0.04);
        l.sendParticles(dust(e), x, y, z, 4, 0.3, 0.3, 0.3, 0.0);
        if (e == Element.ELECTRO) l.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 3, 0.3, 0.3, 0.3, 0.1);
    }

    /** 원거리/기류 표현: 부채꼴 안에 흩뿌리는 흐름 */
    public static void stream(ServerLevel l, Element e, Vec3 c, double spread) {
        l.sendParticles(main(e), c.x, c.y, c.z, 3, spread * 0.35, spread * 0.25, spread * 0.35, 0.02);
        l.sendParticles(dust(e), c.x, c.y, c.z, 2, spread * 0.4, spread * 0.3, spread * 0.4, 0.0);
    }

    public static void orbitPoint(ServerLevel l, Element e, double x, double y, double z) {
        l.sendParticles(main(e), x, y, z, 2, 0.05, 0.05, 0.05, 0.01);
        l.sendParticles(dust(e), x, y, z, 3, 0.1, 0.1, 0.1, 0.0);
        l.sendParticles(sub(e), x, y, z, 1, 0.05, 0.05, 0.05, 0.0);
    }

    public static void summon(ServerLevel l, Element e, Vec3 p) {
        l.sendParticles(dust(e), p.x, p.y, p.z, 4, 0.12, 0.12, 0.12, 0.0);
        l.sendParticles(sub(e), p.x, p.y, p.z, 2, 0.15, 0.15, 0.15, 0.0);
        l.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.08, 0.08, 0.08, 0.0);
    }

    public static void wall(ServerLevel l, Element e, Vec3 c, Vec3 side) {
        ParticleOptions p = dust(e), m = main(e);
        for (int x = -2; x <= 2; x++) {
            for (int y = 0; y < 3; y++) {
                l.sendParticles((x + y) % 2 == 0 ? m : p, c.x + side.x * x, c.y + y + 0.3, c.z + side.z * x,
                        1, 0.05, 0.05, 0.05, 0.0);
            }
        }
    }

    public static void slash(ServerLevel l, Element e, Vec3 eye, Vec3 look) {
        Vec3 c = eye.add(look.scale(1.8));
        Vec3 side = new Vec3(-look.z, 0, look.x);
        side = side.lengthSqr() < 1e-4 ? new Vec3(1, 0, 0) : side.normalize();
        l.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y - 0.2, c.z, 1, 0, 0, 0, 0);
        ParticleOptions p = dust(e);
        for (int i = -2; i <= 2; i++) {
            l.sendParticles(p, c.x + side.x * i * 0.5, c.y - 0.1 * Math.abs(i), c.z + side.z * i * 0.5, 1, 0.02, 0.02, 0.02, 0.0);
        }
    }

    public static void sound(ServerLevel l, Element e, double x, double y, double z, boolean big) {
        SoundEvent s = switch (e) {
            case PYRO -> SoundEvents.FIRECHARGE_USE;
            case HYDRO -> SoundEvents.PLAYER_SPLASH;
            case ELECTRO -> SoundEvents.LIGHTNING_BOLT_IMPACT;
            case CRYO -> SoundEvents.GLASS_BREAK;
            case ANEMO -> SoundEvents.ENDER_DRAGON_FLAP;
            case GEO -> SoundEvents.AMETHYST_CLUSTER_BREAK;
            case DENDRO -> SoundEvents.BONE_MEAL_USE;
        };
        l.playSound(null, x, y, z, s, SoundSource.PLAYERS, big ? 1.0f : 0.5f, big ? 0.9f : 1.2f);
    }
}
