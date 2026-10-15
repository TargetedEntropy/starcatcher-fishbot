package com.targetedentropy.scfishbot;

import com.wdiscute.starcatcher.fish.Rarity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.neoforge.client.gui.GuiLayer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Small status panel: mode, current activity and session statistics.
 */
public final class FishBotHud implements GuiLayer
{
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int MARGIN = 4;
    private static final int BACKGROUND = 0x90000000;
    private static final int BORDER = 0xFF2E8BC0;

    @Override
    public void render(GuiGraphicsExtractor g, DeltaTracker deltaTracker)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui || mc.debugEntries.isOverlayVisible()) return;
        if (!FishBotConfig.HUD_ENABLED.get()) return;

        FishBot bot = FishBot.INSTANCE;
        if (bot.mode() == BotMode.OFF && !bot.recentlyStopped() && !FishBotConfig.HUD_SHOW_WHEN_OFF.get()) return;

        List<Component> lines = buildLines(bot);
        Font font = mc.font;

        int width = 0;
        for (Component line : lines) width = Math.max(width, font.width(line));
        width += PADDING * 2;
        int height = lines.size() * LINE_HEIGHT + PADDING * 2 - 2;

        int screenW = g.guiWidth();
        int screenH = g.guiHeight();
        int x;
        int y;
        switch (FishBotConfig.HUD_CORNER.get())
        {
            case TOP_RIGHT -> { x = screenW - width - MARGIN; y = MARGIN; }
            case BOTTOM_LEFT -> { x = MARGIN; y = screenH - height - MARGIN - 40; }
            case BOTTOM_RIGHT -> { x = screenW - width - MARGIN; y = screenH - height - MARGIN - 40; }
            default -> { x = MARGIN; y = MARGIN; }
        }

        g.fill(x, y, x + width, y + height, BACKGROUND);
        g.outline(x, y, width, height, BORDER);

        int textY = y + PADDING;
        for (Component line : lines)
        {
            g.text(font, line, x + PADDING, textY, 0xFFFFFFFF, true);
            textY += LINE_HEIGHT;
        }
    }

    private static List<Component> buildLines(FishBot bot)
    {
        List<Component> lines = new ArrayList<>();
        SessionStats s = bot.stats();

        lines.add(Component.literal("FishBot ").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD)
                .append(bot.mode().displayName().copy().withStyle(style -> style.withBold(false))));

        MutableComponent status = Component.translatable("scfishbot.status." + bot.status().name().toLowerCase()).withStyle(ChatFormatting.WHITE);
        if (!bot.statusDetail().getString().isEmpty())
            status.append(Component.literal(" (").withStyle(ChatFormatting.GRAY))
                    .append(bot.statusDetail().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(")").withStyle(ChatFormatting.GRAY));
        lines.add(status);

        lines.add(Component.translatable("scfishbot.hud.caught", s.caught, s.minigames,
                String.format("%.0f%%", s.successRate() * 100)).withStyle(ChatFormatting.GREEN));
        lines.add(Component.translatable("scfishbot.hud.perfect_treasure", s.perfect, s.treasures).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("scfishbot.hud.rate", String.format("%.1f", s.catchesPerHour()), formatDuration(s.elapsedMillis()))
                .withStyle(ChatFormatting.YELLOW));

        if (bot.mode() == BotMode.FULL || s.casts > 0)
            lines.add(Component.translatable("scfishbot.hud.casts", s.casts, s.bites, s.hooks).withStyle(ChatFormatting.GRAY));

        int rare = 0;
        for (Map.Entry<Rarity, Integer> e : s.byRarity.entrySet())
            if (e.getKey().ordinal() >= Rarity.RARE.ordinal()) rare += e.getValue();
        if (rare > 0)
            lines.add(Component.translatable("scfishbot.hud.rare", rare).withStyle(ChatFormatting.LIGHT_PURPLE));

        int top = FishBotConfig.HUD_TOP_FISH_LINES.get();
        for (Map.Entry<String, Integer> e : s.topItems(top))
            lines.add(Component.literal(" " + e.getValue() + "x ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.literal(e.getKey()).withStyle(ChatFormatting.WHITE)));

        return lines;
    }

    static String formatDuration(long millis)
    {
        long seconds = millis / 1000;
        long h = seconds / 3600;
        long m = (seconds % 3600) / 60;
        long sec = seconds % 60;
        return h > 0 ? String.format("%d:%02d:%02d", h, m, sec) : String.format("%d:%02d", m, sec);
    }
}
