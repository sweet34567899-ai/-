package com.example.elementalgaze;

public final class GazeCharacter {
    public final String id;              // "elementalgaze:fischl"
    public final Element element;
    public final int rarity;
    public final boolean archon, inPool, provisional;
    public final SkillDef normal, skill, burst;

    public GazeCharacter(String id, Element element, int rarity, boolean archon, boolean inPool, boolean provisional,
                         SkillDef normal, SkillDef skill, SkillDef burst) {
        this.id = id;
        this.element = element;
        this.rarity = rarity;
        this.archon = archon;
        this.inPool = inPool;
        this.provisional = provisional;
        this.normal = normal;
        this.skill = skill;
        this.burst = burst;
    }

    public static String nameKey(String id) {
        return "character." + id.replace(':', '.');
    }
}
