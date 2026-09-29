package com.example.elementalgaze;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** element == null 이면 빈 신의 눈. 소유자 정보는 NBT에 저장. */
public class GazeItem extends Item {
    @Nullable
    public final Element element;

    public GazeItem(@Nullable Element element, Properties props) {
        super(props);
        this.element = element;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return element != null;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tip, TooltipFlag flag) {
        CompoundTag t = stack.getTag();
        if (t != null && t.contains("owner_name")) {
            tip.add(Component.translatable("tooltip.elementalgaze.bound", t.getString("owner_name")).withStyle(ChatFormatting.GRAY));
        }
    }
}
