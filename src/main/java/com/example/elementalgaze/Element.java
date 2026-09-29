package com.example.elementalgaze;

import java.util.Locale;
import javax.annotation.Nullable;

public enum Element {
    PYRO(0xFF7A3D, true), HYDRO(0x4C9BFF, true), ELECTRO(0xB57BFF, true), CRYO(0xA8ECFF, true),
    ANEMO(0x74E6BA, false), GEO(0xF5C84C, false), DENDRO(0x8AD14A, false);

    public static final Element[] VALUES = values();

    public final int color;
    public final boolean aura;   // 원소 부착 가능 여부 (불/물/번개/얼음)
    public final String id;

    Element(int color, boolean aura) {
        this.color = color;
        this.aura = aura;
        this.id = name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public static Element byName(String s) {
        for (Element e : VALUES) if (e.id.equals(s)) return e;
        return null;
    }
}
