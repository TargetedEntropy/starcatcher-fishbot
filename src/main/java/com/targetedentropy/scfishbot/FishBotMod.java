package com.targetedentropy.scfishbot;

import net.minecraft.resources.Identifier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Starcatcher FishBot: a client-only helper that plays Starcatcher's fishing for you.
 */
@Mod(value = FishBotMod.MOD_ID, dist = Dist.CLIENT)
public final class FishBotMod
{
    public static final String MOD_ID = "scfishbot";

    public FishBotMod(IEventBus modBus, ModContainer container)
    {
        container.registerConfig(ModConfig.Type.CLIENT, FishBotConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(RegisterKeyMappingsEvent.class, FishBotKeys::register);
        modBus.addListener(RegisterGuiLayersEvent.class, event -> event.registerAboveAll(id("hud"), new FishBotHud()));

        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event ->
        {
            FishBotKeys.handle();
            FishBot.INSTANCE.onClientTick();
        });
        NeoForge.EVENT_BUS.addListener(RenderFrameEvent.Pre.class, event -> FishBot.INSTANCE.onFrame());
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, event -> FishBotCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingIn.class, event -> FishBot.INSTANCE.onLevelJoined());
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> FishBot.INSTANCE.onLevelLeft());
    }

    public static Identifier id(String path)
    {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }
}
