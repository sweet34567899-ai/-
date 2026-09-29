package com.example.elementalgaze;

import com.google.gson.JsonObject;
import net.minecraft.util.GsonHelper;

/** type: melee/ranged(일반공격), aoe/dash(스킬·폭발) */
public final class SkillDef {
    public final String type;
    public final float mult, range, radius, energyCost, energyGain;
    public final int cooldown, interval;

    public SkillDef(JsonObject o) {
        this.type = GsonHelper.getAsString(o, "type", "aoe");
        this.mult = GsonHelper.getAsFloat(o, "multiplier", 1f);
        this.range = GsonHelper.getAsFloat(o, "range", 0f);
        this.radius = GsonHelper.getAsFloat(o, "radius", 3f);
        this.cooldown = GsonHelper.getAsInt(o, "cooldown", 100);
        this.interval = GsonHelper.getAsInt(o, "interval", 10);
        this.energyCost = GsonHelper.getAsFloat(o, "energy_cost", 60f);
        this.energyGain = GsonHelper.getAsFloat(o, "energy_gain", 0f);
    }
}
