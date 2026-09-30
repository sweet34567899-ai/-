package com.example.elementalgaze.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;

public final class Keys {
    public static final String CATEGORY = "key.categories.elementalgaze";
    public static final KeyMapping COMBAT = new KeyMapping("key.elementalgaze.combat", InputConstants.KEY_R, CATEGORY);
    public static final KeyMapping SKILL = new KeyMapping("key.elementalgaze.skill", InputConstants.KEY_Z, CATEGORY);
    public static final KeyMapping BURST = new KeyMapping("key.elementalgaze.burst", InputConstants.KEY_X, CATEGORY);
    public static final KeyMapping MENU = new KeyMapping("key.elementalgaze.menu", InputConstants.KEY_G, CATEGORY);

    private Keys() {}
}
