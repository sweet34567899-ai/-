package com.example.elementalgaze;

/** 활동별 피로도 파라미터. softCap: 이 값만큼 누적되면 획득량 50%, recoverPerTick: 틱당 회복량. */
public enum Activity {
    KILL(60f, 0.05f), MINE(40f, 0.03f), WOOD(30f, 0.03f), FARM(30f, 0.03f),
    TRAVEL(20f, 0.02f), CRAFT(15f, 0.02f), SMELT(15f, 0.02f), FISH(20f, 0.02f);

    public static final Activity[] VALUES = values();

    public final float softCap;
    public final float recoverPerTick;

    Activity(float softCap, float recoverPerTick) {
        this.softCap = softCap;
        this.recoverPerTick = recoverPerTick;
    }
}
