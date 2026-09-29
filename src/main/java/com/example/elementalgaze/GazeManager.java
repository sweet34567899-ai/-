package com.example.elementalgaze;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.items.ItemHandlerHelper;

/** 진행도·발현·재발현·각성·입력 처리. 모두 서버 측에서만 호출된다. */
public final class GazeManager {
    private GazeManager() {}

    // ---------------- 활동 -> 에너지 ----------------
    /** aff == null 이면 전 원소에 균등 성향. */
    public static void onActivity(ServerPlayer p, Activity a, float base, @Nullable Element aff) {
        if (p instanceof FakePlayer || p.isSpectator()) return;
        PlayerKit kit = PlayerKit.get(p);
        if (kit == null) return;
        long now = p.level().getGameTime();
        float gained = kit.applyFatigue(a, base, now);
        if (gained <= 0f) return;
        if (aff != null) {
            kit.affinity[aff.ordinal()] += gained;
        } else {
            float each = gained / Element.VALUES.length;
            for (int i = 0; i < kit.affinity.length; i++) kit.affinity[i] += each;
        }
        addProgress(p, kit, gained);
    }

    public static void addProgress(ServerPlayer p, PlayerKit kit, float amount) {
        switch (kit.stage) {
            case 0 -> {
                int before = (int) kit.energy;
                kit.energy += amount;
                if (kit.energy >= Config.GAZE_NEEDED.get()) {
                    kit.stage = 1;
                    kit.energy = 0f;
                    give(p, null);
                    p.sendSystemMessage(Component.translatable("msg.elementalgaze.gaze_received"));
                    p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1f, 1f);
                    kit.dirty = true;
                } else if ((int) kit.energy != before) {
                    kit.dirty = true;
                }
            }
            case 1 -> {
                int before = (int) kit.energy;
                kit.energy += amount;
                if (kit.energy >= Config.ELEMENT_NEEDED.get()) {
                    manifest(p, kit);
                } else if ((int) kit.energy != before) {
                    kit.dirty = true;
                }
            }
            default -> {
                GazeCharacter c = CharacterRegistry.get(kit.characterId);
                float max = c != null ? c.burst.energyCost : 100f;
                int before = (int) kit.burstEnergy;
                kit.burstEnergy = Math.min(max, kit.burstEnergy + amount * Config.BURST_CHARGE_MUL.get().floatValue());
                if ((int) kit.burstEnergy != before) kit.dirty = true;
            }
        }
    }

    // ---------------- 발현 ----------------
    public static void manifest(ServerPlayer p, PlayerKit kit) {
        Element el = rollElement(kit, p.getRandom());
        finishRoll(p, kit, el, "");
    }

    /** 성향 점수 + (총합의 30%를 균등 분배)를 가중치로 사용. 기록이 없으면 완전 균등. */
    private static Element rollElement(PlayerKit kit, RandomSource rng) {
        float total = 0f;
        for (float f : kit.affinity) total += f;
        float baseline = total > 0f ? total * 0.3f / Element.VALUES.length : 1f;
        float sum = 0f;
        float[] w = new float[Element.VALUES.length];
        for (int i = 0; i < w.length; i++) {
            w[i] = kit.affinity[i] + baseline;
            sum += w[i];
        }
        float r = rng.nextFloat() * sum;
        for (int i = 0; i < w.length; i++) {
            r -= w[i];
            if (r <= 0f) return Element.VALUES[i];
        }
        return Element.VALUES[Element.VALUES.length - 1];
    }

    private static boolean finishRoll(ServerPlayer p, PlayerKit kit, Element el, String exclude) {
        RandomSource rng = p.getRandom();
        boolean five = kit.pity >= Config.PITY_THRESHOLD.get() || rng.nextDouble() < Config.FIVE_STAR_RATE.get();
        GazeCharacter c = CharacterRegistry.roll(el, five, exclude, rng);
        if (c == null) {
            p.sendSystemMessage(Component.translatable("msg.elementalgaze.no_pool"));
            return false;
        }
        kit.pity = (byte) (c.rarity >= 5 ? 0 : Math.min(100, kit.pity + 1));
        kit.stage = 2;
        kit.element = (byte) el.ordinal();
        kit.characterId = c.id;
        kit.archon = false;
        kit.energy = 0f;
        kit.burstEnergy = 0f;
        kit.skillReadyAt = 0L;
        kit.burstReadyAt = 0L;
        removeGazes(p);
        give(p, el);
        kit.dirty = true;
        Component elName = Component.translatable("element.elementalgaze." + el.id).withStyle(s -> s.withColor(el.color));
        p.sendSystemMessage(Component.translatable("msg.elementalgaze.manifest", elName,
                Component.translatable(GazeCharacter.nameKey(c.id)), c.rarity));
        p.level().playSound(null, p.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 1f, 1.2f);
        p.serverLevel().sendParticles(Fx.particle(el), p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.1);
        return true;
    }

    /** 재발현: 같은 원소 안에서만, 직전 캐릭터 제외. 집정관 각성 후에는 불가. */
    public static boolean reroll(ServerPlayer p) {
        PlayerKit kit = PlayerKit.get(p);
        if (kit == null) return false;
        if (kit.stage != 2) {
            p.sendSystemMessage(Component.translatable("msg.elementalgaze.not_manifested"));
            return false;
        }
        if (kit.archon) {
            p.sendSystemMessage(Component.translatable("msg.elementalgaze.archon_locked"));
            return false;
        }
        return finishRoll(p, kit, Element.VALUES[kit.element], kit.characterId);
    }

    /** 집정관 각성: 이미 발현된 눈의 원소와 아이템 원소가 같을 때만. */
    public static boolean awaken(ServerPlayer p, Element el) {
        PlayerKit kit = PlayerKit.get(p);
        if (kit == null) return false;
        if (kit.stage != 2) {
            p.sendSystemMessage(Component.translatable("msg.elementalgaze.not_manifested"));
            return false;
        }
        if (kit.element != el.ordinal()) {
            p.sendSystemMessage(Component.translatable("msg.elementalgaze.wrong_element"));
            return false;
        }
        if (kit.archon) {
            p.sendSystemMessage(Component.translatable("msg.elementalgaze.already_archon"));
            return false;
        }
        GazeCharacter c = CharacterRegistry.archon(el);
        if (c == null) {
            p.sendSystemMessage(Component.translatable("msg.elementalgaze.no_pool"));
            return false;
        }
        kit.characterId = c.id;
        kit.archon = true;
        kit.burstEnergy = 0f;
        kit.dirty = true;
        p.sendSystemMessage(Component.translatable("msg.elementalgaze.awakened", Component.translatable(GazeCharacter.nameKey(c.id))));
        p.level().playSound(null, p.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1f, 1f);
        p.serverLevel().sendParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
        return true;
    }

    // ---------------- 아이템 ----------------
    public static void give(ServerPlayer p, @Nullable Element el) {
        ItemStack s = new ItemStack(el == null ? ModItems.EMPTY_GAZE.get() : ModItems.GAZE.get(el).get());
        CompoundTag tag = s.getOrCreateTag();
        tag.putUUID("owner", p.getUUID());
        tag.putString("owner_name", p.getGameProfile().getName());
        ItemHandlerHelper.giveItemToPlayer(p, s);
    }

    public static void removeGazes(ServerPlayer p) {
        Inventory inv = p.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.getItem() instanceof GazeItem && s.hasTag() && s.getTag().hasUUID("owner")
                    && s.getTag().getUUID("owner").equals(p.getUUID())) {
                inv.setItem(i, ItemStack.EMPTY);
            }
        }
    }

    // ---------------- 입력 (클라이언트는 "무엇을 눌렀는지"만 보낸다) ----------------
    public static void handleAction(ServerPlayer p, int action) {
        PlayerKit kit = PlayerKit.get(p);
        if (kit == null) return;
        long now = p.level().getGameTime();
        if (now - kit.lastActionAt < 2) return;   // 입력 스팸 방지
        kit.lastActionAt = now;
        if (kit.stage != 2) {
            p.displayClientMessage(Component.translatable("msg.elementalgaze.not_manifested"), true);
            return;
        }
        if (action == 0) {
            kit.combatMode = !kit.combatMode;
            kit.dirty = true;
            return;
        }
        GazeCharacter c = CharacterRegistry.get(kit.characterId);
        if (c == null) return;
        switch (action) {
            case 1 -> { if (kit.combatMode) SkillExecutor.normal(p, kit, c); }
            case 2 -> SkillExecutor.skill(p, kit, c);
            case 3 -> SkillExecutor.burst(p, kit, c);
            default -> { }
        }
    }
}
