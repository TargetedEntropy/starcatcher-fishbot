package com.targetedentropy.scfishbot;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.fml.ModList;
import org.lwjgl.glfw.GLFW;

public final class FishBotKeys
{
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(FishBotMod.id("fishbot"));

    public static final KeyMapping TOGGLE_FULL = key("toggle_full", GLFW.GLFW_KEY_F8);
    public static final KeyMapping TOGGLE_ASSIST = key("toggle_assist", GLFW.GLFW_KEY_F7);
    public static final KeyMapping TOGGLE_HUD = key("toggle_hud", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping RESET_STATS = key("reset_stats", GLFW.GLFW_KEY_UNKNOWN);
    public static final KeyMapping OPEN_CONFIG = key("open_config", GLFW.GLFW_KEY_UNKNOWN);

    private static KeyMapping key(String name, int glfwKey)
    {
        return new KeyMapping("key." + FishBotMod.MOD_ID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM.getOrCreate(glfwKey), CATEGORY);
    }

    static void register(RegisterKeyMappingsEvent event)
    {
        event.registerCategory(CATEGORY);
        event.register(TOGGLE_FULL);
        event.register(TOGGLE_ASSIST);
        event.register(TOGGLE_HUD);
        event.register(RESET_STATS);
        event.register(OPEN_CONFIG);
    }

    static void handle()
    {
        FishBot bot = FishBot.INSTANCE;

        while (TOGGLE_FULL.consumeClick()) bot.toggle(BotMode.FULL);
        while (TOGGLE_ASSIST.consumeClick()) bot.toggle(BotMode.ASSIST);
        while (TOGGLE_HUD.consumeClick())
        {
            FishBotConfig.HUD_ENABLED.set(!FishBotConfig.HUD_ENABLED.get());
            FishBotConfig.HUD_ENABLED.save();
        }
        while (RESET_STATS.consumeClick())
        {
            bot.stats().reset();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.sendOverlayMessage(net.minecraft.network.chat.Component.translatable("scfishbot.message.stats_reset"));
        }
        while (OPEN_CONFIG.consumeClick()) openConfig();
    }

    static void openConfig()
    {
        Minecraft mc = Minecraft.getInstance();
        ModList.get().getModContainerById(FishBotMod.MOD_ID)
                .ifPresent(container -> mc.setScreen(new ConfigurationScreen(container, mc.screen)));
    }

    private FishBotKeys()
    {
    }
}
