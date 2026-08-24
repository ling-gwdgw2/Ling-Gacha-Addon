package com.holysweet.linggacha.server.commands;

import com.holysweet.linggacha.gacha.GachaManager;
import com.holysweet.linggacha.network.GachaNet;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class GachaCommands {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("convene")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        GachaNet.openGachaForPlayer(player);
                        return 1;
                    }
                    return 0;
                });

        // Admin reload sub-command
        root.then(Commands.literal("reload")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    GachaManager.INSTANCE.loadBanners();
                    if (ctx.getSource().getServer() != null) {
                        GachaManager.INSTANCE.syncAllOnlinePlayers(ctx.getSource().getServer());
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Ling Gacha] Banners reloaded & synced to all online players!"), true);
                    return 1;
                }));

        dispatcher.register(root);

        // Also register /gacha alias
        LiteralArgumentBuilder<CommandSourceStack> alias = Commands.literal("gacha")
                .executes(ctx -> {
                    if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
                        GachaNet.openGachaForPlayer(player);
                        return 1;
                    }
                    return 0;
                });

        alias.then(Commands.literal("reload")
                .requires(s -> s.hasPermission(2))
                .executes(ctx -> {
                    GachaManager.INSTANCE.loadBanners();
                    if (ctx.getSource().getServer() != null) {
                        GachaManager.INSTANCE.syncAllOnlinePlayers(ctx.getSource().getServer());
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal("§a[Ling Gacha] Banners reloaded & synced to all online players!"), true);
                    return 1;
                }));

        dispatcher.register(alias);
    }
}
