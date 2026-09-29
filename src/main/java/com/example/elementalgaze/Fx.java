package com.example.elementalgaze;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;

public final class Fx {
    private Fx() {}

    public static ParticleOptions particle(Element e) {
        return switch (e) {
            case PYRO -> ParticleTypes.FLAME;
            case HYDRO -> ParticleTypes.SPLASH;
            case ELECTRO -> ParticleTypes.ELECTRIC_SPARK;
            case CRYO -> ParticleTypes.SNOWFLAKE;
            case ANEMO -> ParticleTypes.CLOUD;
            case GEO -> ParticleTypes.CRIT;
            case DENDRO -> ParticleTypes.HAPPY_VILLAGER;
        };
    }
}
