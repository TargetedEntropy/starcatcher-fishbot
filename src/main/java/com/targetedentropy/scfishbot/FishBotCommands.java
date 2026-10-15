package com.targetedentropy.scfishbot;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.wdiscute.starcatcher.fish.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;

/**
 * Client-side {@code /fishbot} command.
 */
public final class FishBotCommands
{
    static void register(CommandDispatcher<CommandSourceStack> dispatcher)
    {
        dispatcher.register(Commands.literal("fishbot")
                .executes(FishBotCommands::status)
                .then(Commands.literal("help").executes(FishBotCommands::help))
                .then(Commands.literal("full").executes(ctx -> setMode(BotMode.FULL)))
                .then(Commands.literal("on").executes(ctx -> setMode(BotMode.FULL)))
                .then(Commands.literal("assist").executes(ctx -> setMode(BotMode.ASSIST)))
                .then(Commands.literal("off").executes(ctx -> setMode(BotMode.OFF)))
                .then(Commands.literal("status").executes(FishBotCommands::status))
                .then(Commands.literal("stats").executes(FishBotCommands::stats))
                .then(Commands.literal("reset").executes(FishBotCommands::reset))
                .then(Commands.literal("hud").executes(FishBotCommands::toggleHud))
                .then(Commands.literal("config").executes(FishBotCommands::config)));
    }

    private static int setMode(BotMode mode)
    {
        FishBot.INSTANCE.setMode(mode);
        send(Component.translatable("scfishbot.message.mode", mode.displayName()));
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx)
    {
        send(Component.literal("/fishbot full|on").withStyle(ChatFormatting.AQUA).append(text(" - cast, hook, play and recast automatically")));
        send(Component.literal("/fishbot assist").withStyle(ChatFormatting.AQUA).append(text(" - only play the reel minigame")));
        send(Component.literal("/fishbot off").withStyle(ChatFormatting.AQUA).append(text(" - stop the bot")));
        send(Component.literal("/fishbot stats").withStyle(ChatFormatting.AQUA).append(text(" - session statistics")));
        send(Component.literal("/fishbot reset").withStyle(ChatFormatting.AQUA).append(text(" - reset session statistics")));
        send(Component.literal("/fishbot hud").withStyle(ChatFormatting.AQUA).append(text(" - show/hide the overlay")));
        send(Component.literal("/fishbot config").withStyle(ChatFormatting.AQUA).append(text(" - open the settings screen")));
        return 1;
    }

    private static int status(CommandContext<CommandSourceStack> ctx)
    {
        FishBot bot = FishBot.INSTANCE;
        MutableComponent msg = Component.translatable("scfishbot.message.mode", bot.mode().displayName())
                .append(text(" | "))
                .append(Component.translatable("scfishbot.status." + bot.status().name().toLowerCase()));
        if (!bot.statusDetail().getString().isEmpty()) msg.append(text(" (")).append(bot.statusDetail()).append(text(")"));
        send(msg);
        return 1;
    }

    private static int stats(CommandContext<CommandSourceStack> ctx)
    {
        SessionStats s = FishBot.INSTANCE.stats();
        send(Component.literal("--- FishBot session (" + FishBotHud.formatDuration(s.elapsedMillis()) + ") ---").withStyle(ChatFormatting.DARK_AQUA));
        send(Component.translatable("scfishbot.hud.caught", s.caught, s.minigames, String.format("%.0f%%", s.successRate() * 100)).withStyle(ChatFormatting.GREEN));
        send(Component.translatable("scfishbot.hud.perfect_treasure", s.perfect, s.treasures).withStyle(ChatFormatting.GOLD));
        send(Component.translatable("scfishbot.hud.rate", String.format("%.1f", s.catchesPerHour()), FishBotHud.formatDuration(s.elapsedMillis())).withStyle(ChatFormatting.YELLOW));
        send(Component.translatable("scfishbot.hud.casts", s.casts, s.bites, s.hooks).withStyle(ChatFormatting.GRAY));
        send(Component.translatable("scfishbot.message.presses", s.presses, s.rodSwaps).withStyle(ChatFormatting.GRAY));

        if (!s.byRarity.isEmpty())
        {
            MutableComponent rarities = Component.translatable("scfishbot.message.by_rarity").withStyle(ChatFormatting.LIGHT_PURPLE);
            for (Map.Entry<Rarity, Integer> e : s.byRarity.entrySet())
                rarities.append(text(" " + e.getKey().getSerializedName() + "=" + e.getValue()));
            send(rarities);
        }

        for (Map.Entry<String, Integer> e : s.topItems(10))
            send(Component.literal("  " + e.getValue() + "x ").withStyle(ChatFormatting.DARK_AQUA).append(text(e.getKey())));
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> ctx)
    {
        FishBot.INSTANCE.stats().reset();
        send(Component.translatable("scfishbot.message.stats_reset"));
        return 1;
    }

    private static int toggleHud(CommandContext<CommandSourceStack> ctx)
    {
        boolean enabled = !FishBotConfig.HUD_ENABLED.get();
        FishBotConfig.HUD_ENABLED.set(enabled);
        FishBotConfig.HUD_ENABLED.save();
        send(Component.translatable(enabled ? "scfishbot.message.hud_on" : "scfishbot.message.hud_off"));
        return 1;
    }

    private static int config(CommandContext<CommandSourceStack> ctx)
    {
        // The chat screen closes after the command runs; open the config on the next tick.
        Minecraft.getInstance().schedule(FishBotKeys::openConfig);
        return 1;
    }

    private static MutableComponent text(String s)
    {
        return Component.literal(s).withStyle(ChatFormatting.WHITE);
    }

    private static void send(Component message)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.sendSystemMessage(message);
    }

    private FishBotCommands()
    {
    }
}
