package com.example.elementalgaze;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/**
 * type: melee/ranged(일반공격) | aoe, dash, cone, strikes, field, orbit, summon, barrier, boomerang, delayed_burst
 * 필드 의미는 SkillExecutor 의 각 동작 주석 참고.
 */
public final class SkillDef {
    public final String type, style;
    public final float mult, range, radius, aoe, pull, cone, energyCost, energyGain;
    public final int cooldown, interval, duration, tick, waves, hits, arms, delay;
    public final boolean follow, progressive;

    public SkillDef(JsonObject o) {
        this.type = GsonHelper.getAsString(o, "type", "aoe");
        this.style = GsonHelper.getAsString(o, "style", "");
        this.mult = GsonHelper.getAsFloat(o, "multiplier", 1f);
        this.range = GsonHelper.getAsFloat(o, "range", 0f);
        this.radius = GsonHelper.getAsFloat(o, "radius", 3f);
        this.aoe = GsonHelper.getAsFloat(o, "aoe", 3f);
        this.pull = GsonHelper.getAsFloat(o, "pull", 0f);
        this.cone = GsonHelper.getAsFloat(o, "cone", 0.5f);
        this.energyCost = GsonHelper.getAsFloat(o, "energy_cost", 60f);
        this.energyGain = GsonHelper.getAsFloat(o, "energy_gain", 0f);
        this.cooldown = GsonHelper.getAsInt(o, "cooldown", 100);
        this.interval = GsonHelper.getAsInt(o, "interval", 10);
        this.duration = GsonHelper.getAsInt(o, "duration", 0);
        this.tick = GsonHelper.getAsInt(o, "tick", 10);
        this.waves = GsonHelper.getAsInt(o, "waves", 1);
        this.hits = GsonHelper.getAsInt(o, "hits", 16);
        this.arms = GsonHelper.getAsInt(o, "arms", 3);
        this.delay = GsonHelper.getAsInt(o, "delay", 20);
        this.follow = GsonHelper.getAsBoolean(o, "follow", false);
        this.progressive = GsonHelper.getAsBoolean(o, "progressive", false);
    }
}
