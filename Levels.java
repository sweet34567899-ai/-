package com.example.elementalgaze;

/** 경험치 곡선과 스킬/스탯 레벨 수치. 서버·클라이언트가 같은 식을 쓴다. */
public final class Levels {
    public static final int SLOTS = 7;
    public static final int NORMAL = 0, SKILL = 1, BURST = 2, ATK = 3, HP = 4, CRIT = 5, MASTERY = 6;
    public static final float CRIT_DMG = 1.6f;

    private Levels() {}

    public static int cap(int slot) { return slot <= BURST ? 9 : 20; }
    public static float xpNeed(int level) { return 60f + 30f * level + 4f * level * level; }
    public static float skillMul(byte[] up, int slot) { return 1f + 0.12f * up[slot]; }
    public static float atkMul(byte[] up, int level) { return (1f + 0.04f * up[ATK]) * (1f + 0.01f * (level - 1)); }
    public static float critRate(byte[] up) { return 0.05f + 0.015f * up[CRIT]; }
    public static float mastery(byte[] up) { return 0.03f * up[MASTERY]; }
    public static double bonusHp(byte[] up) { return 2.0 * up[HP]; }
}
