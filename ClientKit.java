package com.example.elementalgaze;

/** 클라이언트 표시용 상태 캐시. 원시 타입만 들고 있어 서버에서 로드돼도 안전하다. */
public final class ClientKit {
    public static byte stage, element = -1, pity, rarity;
    public static String characterId = "";
    public static float energy, energyMax = 100f, burst, burstCost, xp, xpNeed = 100f;
    public static long skillAt, burstAt;
    public static int skillCd, burstCd, level = 1, points;
    public static boolean combat, archon;
    public static final byte[] up = new byte[Levels.SLOTS];

    private ClientKit() {}

    public static void update(SyncPacket m) {
        stage = m.stage; element = m.element; pity = m.pity; rarity = m.rarity; characterId = m.characterId;
        energy = m.energy; energyMax = m.energyMax; burst = m.burst; burstCost = m.burstCost;
        xp = m.xp; xpNeed = m.xpNeed;
        skillAt = m.skillAt; burstAt = m.burstAt; skillCd = m.skillCd; burstCd = m.burstCd;
        level = m.level; points = m.points; combat = m.combat; archon = m.archon;
        System.arraycopy(m.up, 0, up, 0, Levels.SLOTS);
    }
}
