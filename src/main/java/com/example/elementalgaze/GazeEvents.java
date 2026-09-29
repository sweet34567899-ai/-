package com.example.elementalgaze;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 이벤트 기반 활동 수집. 틱 이벤트는 5틱마다 동기화/20틱마다 이동 계산만 한다. */
@Mod.EventBusSubscriber(modid = ElementalGaze.MODID)
public final class GazeEvents {
    private GazeEvents() {}

    // ---------- 인프라 ----------
    @SubscribeEvent
    public static void attach(AttachCapabilitiesEvent<Entity> e) {
        if (e.getObject() instanceof Player) {
            e.addCapability(new ResourceLocation(ElementalGaze.MODID, "kit"), new KitProvider());
        }
    }

    @SubscribeEvent
    public static void clone(PlayerEvent.Clone e) {
        e.getOriginal().reviveCaps();
        PlayerKit o = PlayerKit.get(e.getOriginal());
        PlayerKit n = PlayerKit.get(e.getEntity());
        if (o != null && n != null) n.load(o.save());
        e.getOriginal().invalidateCaps();
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent e) {
        e.addListener(new CharacterRegistry());
    }

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent e) {
        GazeCommand.register(e.getDispatcher());
    }

    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Net.sync(p);
    }

    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Net.sync(p);
    }

    @SubscribeEvent
    public static void dim(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) Net.sync(p);
    }

    // ---------- 활동 ----------
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getPlayer() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        BlockState st = e.getState();
        if (st.is(Tags.Blocks.ORES)) {
            GazeManager.onActivity(p, Activity.MINE, 1.5f, Element.GEO);
        } else if (st.is(BlockTags.LOGS)) {
            GazeManager.onActivity(p, Activity.WOOD, 0.6f, Element.DENDRO);
        } else if (st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st)) {
            GazeManager.onActivity(p, Activity.FARM, 0.8f, Element.DENDRO);   // 다 자란 작물만
        } else if (st.is(Tags.Blocks.STONE)) {
            GazeManager.onActivity(p, Activity.MINE, 0.15f, Element.GEO);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (dead.level().isClientSide || !(dead instanceof Mob)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        float base = 3f + Math.min(dead.getMaxHealth() / 20f, 5f);   // 체력 비례
        if (dead.getType().is(Tags.EntityTypes.BOSSES)) base += 8f;
        if (!(dead instanceof Enemy)) base *= 0.25f;                  // 비적대 몹은 1/4
        GazeManager.onActivity(p, Activity.KILL, base, null);         // 전투는 전 원소 균등
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) GazeManager.onActivity(p, Activity.CRAFT, 0.3f, Element.PYRO);
    }

    @SubscribeEvent
    public static void onSmelt(PlayerEvent.ItemSmeltedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) {
            GazeManager.onActivity(p, Activity.SMELT, 0.2f * Math.min(e.getSmelting().getCount(), 16), Element.PYRO);
        }
    }

    @SubscribeEvent
    public static void onFish(ItemFishedEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) GazeManager.onActivity(p, Activity.FISH, 1.5f, Element.HYDRO);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side.isClient() || !(e.player instanceof ServerPlayer p)) return;
        int t = p.tickCount;
        if (t % 5 != 0) return;
        PlayerKit kit = PlayerKit.get(p);
        if (kit == null) return;
        if (t % 20 == 0) travel(p, kit);
        if (kit.dirty) Net.sync(p);
    }

    private static void travel(ServerPlayer p, PlayerKit kit) {
        double dx = p.getX() - kit.lastX, dz = p.getZ() - kit.lastZ;
        boolean had = kit.hasLast;
        kit.lastX = p.getX();
        kit.lastZ = p.getZ();
        kit.hasLast = true;
        if (!had) return;
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d < 1.0 || d > 12.0) return;                    // 제자리/순간이동 무시
        float mul = p.isPassenger() ? 0.3f : 1f;            // 탈것은 계수 낮게
        GazeManager.onActivity(p, Activity.TRAVEL, (float) Math.min(d, 10.0) * 0.05f * mul, travelElement(p));
    }

    private static Element travelElement(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        BlockPos pos = p.blockPosition();
        if (p.isInWaterOrRain()) return Element.HYDRO;
        if (lvl.isThundering() && lvl.canSeeSky(pos)) return Element.ELECTRO;
        if (lvl.getBiome(pos).value().coldEnoughToSnow(pos)) return Element.CRYO;
        return Element.ANEMO;
    }
}
