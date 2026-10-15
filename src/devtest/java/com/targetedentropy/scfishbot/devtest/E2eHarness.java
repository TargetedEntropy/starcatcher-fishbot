package com.targetedentropy.scfishbot.devtest;

import com.targetedentropy.scfishbot.BotMode;
import com.targetedentropy.scfishbot.FishBot;
import com.targetedentropy.scfishbot.SessionStats;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Dev-only end-to-end check: creates a flat world, digs a pool, gives a Starcatcher rod, switches
 * the bot to FULL and waits for a number of catches, then prints a result line and quits.
 * Only active with {@code -Dscfishbot.e2e=true} (the {@code runE2e} Gradle task).
 */
@Mod(value = "scfishbot_devtest", dist = Dist.CLIENT)
public final class E2eHarness
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_MS = Long.getLong("scfishbot.e2e.timeoutMs", 12 * 60_000L);

    private enum Phase { WAIT_TITLE, WAIT_WORLD, SETUP, START_BOT, RUNNING, DONE }

    private Phase phase = Phase.WAIT_TITLE;
    private int ticks;
    private long startedAt;
    private final int targetCatches = Integer.getInteger("scfishbot.e2e.catches", 5);

    public E2eHarness()
    {
        if (!Boolean.getBoolean("scfishbot.e2e")) return;
        LOGGER.info("[E2E] harness active, target {} catches", targetCatches);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
    }

    private void tick()
    {
        Minecraft mc = Minecraft.getInstance();
        ticks++;

        switch (phase)
        {
            case WAIT_TITLE ->
            {
                // A fresh game directory opens the accessibility onboarding screen first.
                if (mc.getOverlay() == null && mc.level == null && mc.screen != null && ticks > 40)
                {
                    String name = "fishbot_e2e_" + System.currentTimeMillis();
                    LOGGER.info("[E2E] creating world {}", name);
                    LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL,
                            new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false),
                            true, WorldDataConfiguration.DEFAULT);
                    phase = Phase.WAIT_WORLD;
                    mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(12345L, false, false),
                            WorldPresets::createFlatWorldDimensions, new TitleScreen());
                }
            }
            case WAIT_WORLD ->
            {
                if (mc.player != null && mc.level != null && mc.getSingleplayerServer() != null && mc.screen == null)
                {
                    ticks = 0;
                    phase = Phase.SETUP;
                }
            }
            case SETUP ->
            {
                if (ticks < 60) return;
                MinecraftServer server = mc.getSingleplayerServer();
                String who = mc.player.getGameProfile().name();
                server.execute(() ->
                {
                    run(server, "time set day");
                    run(server, "weather clear");
                    net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayerByName(who);
                    net.minecraft.core.BlockPos feet = sp.blockPosition();
                    int placed = 0;
                    for (int x = -5; x <= 5; x++)
                        for (int y = -3; y <= -1; y++)
                            for (int z = 2; z <= 12; z++)
                                if (sp.level().setBlockAndUpdate(feet.offset(x, y, z), net.minecraft.world.level.block.Blocks.WATER.defaultBlockState()))
                                    placed++;
                    LOGGER.info("[E2E] placed {} water blocks in front of {}", placed, feet);
                    run(server, "tp " + who + " ~ ~ ~ 0 40");
                    run(server, "give " + who + " starcatcher:starcatcher_rod");
                });
                ticks = 0;
                phase = Phase.START_BOT;
            }
            case START_BOT ->
            {
                if (ticks < 40) return;
                LOGGER.info("[E2E] main hand before bot: {}", mc.player.getMainHandItem());
                startedAt = System.currentTimeMillis();
                FishBot.INSTANCE.setMode(BotMode.FULL);
                phase = Phase.RUNNING;
                ticks = 0;
            }
            case RUNNING ->
            {
                FishBot bot = FishBot.INSTANCE;
                SessionStats s = bot.stats();
                if (ticks % 100 == 0)
                    LOGGER.info("[E2E] t={}s mode={} status={} {} | casts={} bites={} hooks={} games={} caught={} failed={} perfect={} treasure={} presses={}",
                            (System.currentTimeMillis() - startedAt) / 1000, bot.mode(), bot.status(), bot.statusDetail().getString(),
                            s.casts, s.bites, s.hooks, s.minigames, s.caught, s.failed, s.perfect, s.treasures, s.presses);

                boolean timedOut = System.currentTimeMillis() - startedAt > TIMEOUT_MS;
                if (s.caught >= targetCatches || timedOut || bot.mode() == BotMode.OFF)
                {
                    LOGGER.info("[E2E] RESULT {} caught={} failed={} perfect={} treasure={} casts={} bites={} hooks={} presses={} items={} stopReason='{}'",
                            s.caught >= targetCatches && s.failed == 0 ? "PASS" : "FAIL",
                            s.caught, s.failed, s.perfect, s.treasures, s.casts, s.bites, s.hooks, s.presses, s.byItem,
                            bot.statusDetail().getString());
                    phase = Phase.DONE;
                    bot.setMode(BotMode.OFF);
                    mc.stop();
                }
            }
            case DONE ->
            {
            }
        }
    }

    private static void run(MinecraftServer server, String command)
    {
        LOGGER.info("[E2E] /{}", command);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
    }
}
