package com.example.elementalgaze;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** 테스트용 관리자 명령어 (권한 레벨 2). */
public final class GazeCommand {
    private GazeCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("gaze").requires(s -> s.hasPermission(2))
                .then(Commands.literal("status").executes(c -> status(c.getSource())))
                .then(Commands.literal("energy")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0f))
                                .executes(c -> energy(c.getSource(), FloatArgumentType.getFloat(c, "amount")))))
                .then(Commands.literal("roll").executes(c -> roll(c.getSource())))
                .then(Commands.literal("reset").executes(c -> reset(c.getSource())))
                .then(Commands.literal("setchar")
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(c -> setChar(c.getSource(), StringArgumentType.getString(c, "id"))))));
    }

    private static int status(CommandSourceStack s) throws CommandSyntaxException {
        PlayerKit k = PlayerKit.get(s.getPlayerOrException());
        String m = String.format(Locale.ROOT, "stage=%d energy=%.1f element=%s char=%s archon=%b pity=%d burst=%.0f",
                k.stage, k.energy, k.element < 0 ? "-" : Element.VALUES[k.element].id, k.characterId, k.archon, k.pity, k.burstEnergy);
        s.sendSuccess(() -> Component.literal(m), false);
        return 1;
    }

    private static int energy(CommandSourceStack s, float amount) throws CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        PlayerKit k = PlayerKit.get(p);
        GazeManager.addProgress(p, k, amount);
        s.sendSuccess(() -> Component.literal("+" + amount), false);
        return 1;
    }

    private static int roll(CommandSourceStack s) throws CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        GazeManager.manifest(p, PlayerKit.get(p));
        return 1;
    }

    private static int reset(CommandSourceStack s) throws CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        PlayerKit k = PlayerKit.get(p);
        GazeManager.removeGazes(p);
        k.load(new CompoundTag());
        Net.sync(p);
        s.sendSuccess(() -> Component.literal("reset"), false);
        return 1;
    }

    private static int setChar(CommandSourceStack s, String id) throws CommandSyntaxException {
        ServerPlayer p = s.getPlayerOrException();
        PlayerKit k = PlayerKit.get(p);
        GazeCharacter c = CharacterRegistry.get(id.contains(":") ? id : ElementalGaze.MODID + ":" + id);
        if (c == null) {
            s.sendFailure(Component.literal("unknown character: " + id));
            return 0;
        }
        GazeManager.removeGazes(p);
        GazeManager.give(p, c.element);
        k.stage = 2;
        k.element = (byte) c.element.ordinal();
        k.characterId = c.id;
        k.archon = c.archon;
        k.burstEnergy = 0f;
        k.dirty = true;
        Net.sync(p);
        s.sendSuccess(() -> Component.literal("set " + c.id), false);
        return 1;
    }
}
