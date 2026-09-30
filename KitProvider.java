package com.example.elementalgaze;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.LazyOptional;

public class KitProvider implements ICapabilitySerializable<CompoundTag> {
    private final PlayerKit kit = new PlayerKit();
    private final LazyOptional<PlayerKit> opt = LazyOptional.of(() -> kit);

    @Override
    public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
        return PlayerKit.CAP.orEmpty(cap, opt);
    }

    @Override
    public CompoundTag serializeNBT() {
        return kit.save();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        kit.load(nbt);
    }
}
