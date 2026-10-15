package com.targetedentropy.scfishbot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

public enum BotMode
{
    /** Bot does nothing. */
    OFF(ChatFormatting.RED),
    /** Only plays the reel minigame for you; you cast and hook yourself. */
    ASSIST(ChatFormatting.AQUA),
    /** Casts, hooks bites, plays the minigame and recasts. */
    FULL(ChatFormatting.GREEN);

    public final ChatFormatting color;

    BotMode(ChatFormatting color)
    {
        this.color = color;
    }

    public boolean solvesMinigame()
    {
        return this != OFF;
    }

    public Component displayName()
    {
        return Component.translatable("scfishbot.mode." + name().toLowerCase()).withStyle(color);
    }
}
