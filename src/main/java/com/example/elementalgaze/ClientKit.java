package com.example.elementalgaze;

/** 클라이언트 표시용 상태 캐시. 원시 타입만 들고 있어 서버에서 로드돼도 안전하다. */
public final class ClientKit {
    public static byte stage, element = -1, pity;
    public static String characterId = "";
    public static float energy, energyMax = 100f, burst, burstCost;
    public static long skillAt, burstAt;
    public static boolean combat, archon;

    private ClientKit() {}

    public static void update(SyncPacket m) {
        stage = m.stage; element = m.element; pity = m.pity; characterId = m.characterId;
        energy = m.energy; energyMax = m.energyMax; burst = m.burst; burstCost = m.burstCost;
        skillAt = m.skillAt; burstAt = m.burstAt; combat = m.combat; archon = m.archon;
    }
}
