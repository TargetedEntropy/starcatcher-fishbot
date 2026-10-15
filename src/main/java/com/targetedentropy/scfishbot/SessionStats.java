package com.targetedentropy.scfishbot;

import com.mojang.logging.LogUtils;
import com.wdiscute.starcatcher.fish.Rarity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Counters for the current session plus an optional CSV log of every minigame.
 */
public final class SessionStats
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String CSV_HEADER = "timestamp,result,item_id,item_name,rarity,perfect,treasure,duration_ticks,hits,bot_presses,mode\n";

    public long startedAtMillis = System.currentTimeMillis();
    public int casts;
    public int bites;
    public int hooks;
    public int minigames;
    public int caught;
    public int failed;
    public int perfect;
    public int treasures;
    public int presses;
    public int rodSwaps;

    public final EnumMap<Rarity, Integer> byRarity = new EnumMap<>(Rarity.class);
    public final Map<String, Integer> byItem = new LinkedHashMap<>();

    public void reset()
    {
        startedAtMillis = System.currentTimeMillis();
        casts = bites = hooks = minigames = caught = failed = perfect = treasures = presses = rodSwaps = 0;
        byRarity.clear();
        byItem.clear();
    }

    public long elapsedMillis()
    {
        return System.currentTimeMillis() - startedAtMillis;
    }

    public double catchesPerHour()
    {
        double hours = elapsedMillis() / 3_600_000.0;
        return hours < 1.0 / 120 ? 0 : caught / hours;
    }

    public double successRate()
    {
        return minigames == 0 ? 0 : (double) caught / minigames;
    }

    public List<Map.Entry<String, Integer>> topItems(int limit)
    {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(byItem.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list.subList(0, Math.min(limit, list.size()));
    }

    public void recordMinigame(MinigameResult result, BotMode mode)
    {
        minigames++;
        presses += result.botPresses();
        if (result.success())
        {
            caught++;
            if (result.perfect()) perfect++;
            if (result.treasure()) treasures++;
            byRarity.merge(result.rarity(), 1, Integer::sum);
            byItem.merge(result.itemName(), 1, Integer::sum);
        }
        else
        {
            failed++;
        }

        if (FishBotConfig.LOG_CATCHES_TO_FILE.get()) appendCsv(result, mode);
    }

    private static void appendCsv(MinigameResult r, BotMode mode)
    {
        try
        {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve(FishBotMod.MOD_ID);
            Files.createDirectories(dir);
            Path file = dir.resolve("catches.csv");
            if (!Files.exists(file))
                Files.writeString(file, CSV_HEADER, StandardCharsets.UTF_8, StandardOpenOption.CREATE);

            String line = String.join(",",
                    LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                    r.success() ? "caught" : "lost",
                    csv(r.itemId()),
                    csv(r.itemName()),
                    r.rarity().getSerializedName(),
                    Boolean.toString(r.perfect()),
                    Boolean.toString(r.treasure()),
                    Integer.toString(r.durationTicks()),
                    Integer.toString(r.hits()),
                    Integer.toString(r.botPresses()),
                    mode.name()) + "\n";

            Files.writeString(file, line, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        }
        catch (IOException e)
        {
            LOGGER.warn("Could not write FishBot catch log", e);
        }
    }

    private static String csv(String value)
    {
        if (value.contains(",") || value.contains("\"") || value.contains("\n"))
            return "\"" + value.replace("\"", "\"\"") + "\"";
        return value;
    }

    public record MinigameResult(boolean success, String itemId, String itemName, Rarity rarity,
                                 boolean perfect, boolean treasure, int durationTicks, int hits, int botPresses)
    {
        public static MinigameResult of(boolean success, ItemStack item, Rarity rarity, boolean perfect,
                                        boolean treasure, int durationTicks, int hits, int botPresses)
        {
            return new MinigameResult(success,
                    BuiltInRegistries.ITEM.getKey(item.getItem()).toString(),
                    item.getHoverName().getString(),
                    rarity, perfect, treasure, durationTicks, hits, botPresses);
        }
    }
}
