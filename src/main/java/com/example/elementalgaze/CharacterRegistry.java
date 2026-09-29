package com.example.elementalgaze;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.util.profiling.ProfilerFiller;

/** data/<ns>/gaze_characters/*.json 로더. 로드 시점에 [원소][희귀도] 배열로 미리 분리한다. */
public final class CharacterRegistry extends SimpleJsonResourceReloadListener {
    private static volatile Map<String, GazeCharacter> BY_ID = new HashMap<>();
    private static volatile GazeCharacter[][][] POOL = new GazeCharacter[Element.VALUES.length][2][0];
    private static volatile GazeCharacter[] ARCHON = new GazeCharacter[Element.VALUES.length];

    public CharacterRegistry() {
        super(new Gson(), "gaze_characters");
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> in, ResourceManager rm, ProfilerFiller pf) {
        Map<String, GazeCharacter> map = new HashMap<>();
        for (Map.Entry<ResourceLocation, JsonElement> en : in.entrySet()) {
            try {
                JsonObject o = GsonHelper.convertToJsonObject(en.getValue(), "character");
                Element el = Element.byName(GsonHelper.getAsString(o, "element"));
                if (el == null) throw new IllegalArgumentException("unknown element");
                String id = en.getKey().toString();
                map.put(id, new GazeCharacter(id, el,
                        GsonHelper.getAsInt(o, "rarity", 4),
                        GsonHelper.getAsBoolean(o, "archon", false),
                        GsonHelper.getAsBoolean(o, "in_roll_pool", true),
                        GsonHelper.getAsBoolean(o, "provisional", false),
                        new SkillDef(GsonHelper.getAsJsonObject(o, "normal")),
                        new SkillDef(GsonHelper.getAsJsonObject(o, "skill")),
                        new SkillDef(GsonHelper.getAsJsonObject(o, "burst"))));
            } catch (Exception ex) {
                ElementalGaze.LOGGER.error("Bad gaze character {}: {}", en.getKey(), ex.toString());
            }
        }
        GazeCharacter[][][] pool = new GazeCharacter[Element.VALUES.length][2][];
        GazeCharacter[] archons = new GazeCharacter[Element.VALUES.length];
        for (Element el : Element.VALUES) {
            for (int r = 0; r < 2; r++) {
                final int rr = r;
                pool[el.ordinal()][r] = map.values().stream()
                        .filter(c -> c.element == el && !c.archon && c.inPool && (c.rarity >= 5 ? 1 : 0) == rr)
                        .sorted(Comparator.comparing((GazeCharacter c) -> c.id))
                        .toArray(GazeCharacter[]::new);
            }
            for (GazeCharacter c : map.values()) if (c.archon && c.element == el) archons[el.ordinal()] = c;
        }
        BY_ID = map;
        POOL = pool;
        ARCHON = archons;
        ElementalGaze.LOGGER.info("Loaded {} gaze characters", map.size());
    }

    @Nullable
    public static GazeCharacter get(String id) {
        return BY_ID.get(id);
    }

    @Nullable
    public static GazeCharacter archon(Element e) {
        return ARCHON[e.ordinal()];
    }

    /** 희귀도 먼저, 캐릭터 나중. 빈 풀이면 반대 희귀도로 폴백. exclude 캐릭터는 가능하면 피한다. */
    @Nullable
    public static GazeCharacter roll(Element e, boolean five, String exclude, RandomSource rng) {
        GazeCharacter[][] p = POOL[e.ordinal()];
        GazeCharacter[] pool = p[five ? 1 : 0];
        if (pool.length == 0) pool = p[five ? 0 : 1];
        if (pool.length == 0) return null;
        GazeCharacter c = pool[rng.nextInt(pool.length)];
        for (int i = 0; i < 6 && pool.length > 1 && c.id.equals(exclude); i++) c = pool[rng.nextInt(pool.length)];
        return c;
    }
}
