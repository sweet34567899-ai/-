package com.example.elementalgaze;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ElementalGaze.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ElementalGaze.MODID);

    public static final RegistryObject<Item> EMPTY_GAZE =
            ITEMS.register("empty_gaze", () -> new GazeItem(null, new Item.Properties().stacksTo(1).fireResistant()));
    public static final RegistryObject<Item> REROLL_TOKEN =
            ITEMS.register("reroll_token", () -> new RerollTokenItem(new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final Map<Element, RegistryObject<Item>> GAZE = new EnumMap<>(Element.class);
    public static final Map<Element, RegistryObject<Item>> TRACE = new EnumMap<>(Element.class);

    static {
        for (Element e : Element.VALUES) {
            GAZE.put(e, ITEMS.register("gaze_" + e.id,
                    () -> new GazeItem(e, new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC))));
            TRACE.put(e, ITEMS.register("archon_trace_" + e.id,
                    () -> new ArchonTraceItem(e, new Item.Properties().stacksTo(16).rarity(Rarity.EPIC))));
        }
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.elementalgaze"))
                    .icon(() -> new ItemStack(EMPTY_GAZE.get()))
                    .displayItems((params, out) -> ITEMS.getEntries().forEach(o -> out.accept(o.get())))
                    .build());

    private ModItems() {}
}
