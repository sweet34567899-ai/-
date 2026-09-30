package com.example.elementalgaze;

import net.minecraftforge.common.ForgeConfigSpec;

public final class Config {
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue FIVE_STAR_RATE, GAZE_NEEDED, ELEMENT_NEEDED, BURST_CHARGE_MUL, MIN_BASE_ATK, XP_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue PITY_THRESHOLD, MAX_TARGETS, MAX_LEVEL;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        FIVE_STAR_RATE = b.comment("5성 기본 확률").defineInRange("fiveStarRate", 0.20, 0.0, 1.0);
        PITY_THRESHOLD = b.comment("4성이 이 횟수만큼 연속으로 나오면 다음은 5성 확정").defineInRange("pityThreshold", 5, 1, 100);
        GAZE_NEEDED = b.comment("빈 신의 눈까지 필요한 시선 에너지").defineInRange("gazeEnergyNeeded", 100.0, 1.0, 100000.0);
        ELEMENT_NEEDED = b.comment("원소 발현까지 필요한 원소 에너지").defineInRange("elementEnergyNeeded", 100.0, 1.0, 100000.0);
        BURST_CHARGE_MUL = b.comment("발현 후 활동 에너지 -> 원소폭발 에너지 변환 배율").defineInRange("burstChargeMultiplier", 2.0, 0.0, 100.0);
        MIN_BASE_ATK = b.comment("스킬 기준 공격력 최솟값 (실제 기준값은 플레이어 공격력 속성)").defineInRange("minBaseAttack", 3.0, 0.0, 1000.0);
        MAX_TARGETS = b.comment("스킬 1회당 최대 타격 대상 수").defineInRange("maxTargetsPerSkill", 16, 1, 128);
        XP_MULTIPLIER = b.comment("활동 에너지 -> 경험치 변환 배율").defineInRange("xpMultiplier", 3.0, 0.0, 1000.0);
        MAX_LEVEL = b.comment("최대 레벨").defineInRange("maxLevel", 60, 1, 1000);
        SPEC = b.build();
    }

    private Config() {}
}
