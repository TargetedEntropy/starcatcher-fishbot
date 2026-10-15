package com.targetedentropy.scfishbot;

import com.wdiscute.starcatcher.fish.Rarity;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config, stored in config/scfishbot-client.toml and editable in-game through
 * Mods > Starcatcher FishBot > Config.
 */
public final class FishBotConfig
{
    public static final ModConfigSpec SPEC;

    // general
    public static final ModConfigSpec.EnumValue<BotMode> DEFAULT_MODE;
    public static final ModConfigSpec.BooleanValue ANNOUNCE_STATE_CHANGES;

    // minigame
    public static final ModConfigSpec.BooleanValue COLLECT_TREASURE;
    public static final ModConfigSpec.BooleanValue TREASURE_PRIORITY;
    public static final ModConfigSpec.DoubleValue TREASURE_HOLD_PERCENT;
    public static final ModConfigSpec.BooleanValue AVOID_NEGATIVE_SPOTS;
    public static final ModConfigSpec.BooleanValue HANDLE_MULTI_LAYER;
    public static final ModConfigSpec.IntValue MIN_HIT_INTERVAL_MS;
    public static final ModConfigSpec.IntValue HIT_INTERVAL_JITTER_MS;
    public static final ModConfigSpec.DoubleValue AIM_CENTER_BIAS;
    public static final ModConfigSpec.DoubleValue SKIP_CHANCE;
    public static final ModConfigSpec.IntValue START_DELAY_MS;

    // automation
    public static final ModConfigSpec.IntValue HOOK_DELAY_MIN_TICKS;
    public static final ModConfigSpec.IntValue HOOK_DELAY_MAX_TICKS;
    public static final ModConfigSpec.IntValue RECAST_DELAY_MIN_TICKS;
    public static final ModConfigSpec.IntValue RECAST_DELAY_MAX_TICKS;
    public static final ModConfigSpec.IntValue BITE_TIMEOUT_SECONDS;
    public static final ModConfigSpec.IntValue FLYING_TIMEOUT_SECONDS;
    public static final ModConfigSpec.BooleanValue AUTO_SELECT_ROD;

    // safety
    public static final ModConfigSpec.IntValue MIN_ROD_DURABILITY;
    public static final ModConfigSpec.BooleanValue STOP_WHEN_BAIT_EMPTY;
    public static final ModConfigSpec.BooleanValue STOP_WHEN_INVENTORY_FULL;
    public static final ModConfigSpec.BooleanValue STOP_ON_DAMAGE;
    public static final ModConfigSpec.DoubleValue MIN_HEALTH_PERCENT;
    public static final ModConfigSpec.IntValue HOSTILE_PAUSE_RADIUS;
    public static final ModConfigSpec.IntValue MAX_CATCHES;
    public static final ModConfigSpec.IntValue MAX_SESSION_MINUTES;

    // notifications & logging
    public static final ModConfigSpec.EnumValue<Rarity> ALERT_RARITY;
    public static final ModConfigSpec.BooleanValue ALERT_SOUND;
    public static final ModConfigSpec.BooleanValue CHAT_CATCH_SUMMARY;
    public static final ModConfigSpec.BooleanValue LOG_CATCHES_TO_FILE;

    // hud
    public static final ModConfigSpec.BooleanValue HUD_ENABLED;
    public static final ModConfigSpec.BooleanValue HUD_SHOW_WHEN_OFF;
    public static final ModConfigSpec.EnumValue<HudCorner> HUD_CORNER;
    public static final ModConfigSpec.IntValue HUD_TOP_FISH_LINES;

    public enum HudCorner
    {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    static
    {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("General bot behaviour.").translation(key("general")).push("general");
        DEFAULT_MODE = b.comment("Mode the bot starts in when you join a world. OFF, ASSIST (minigame only) or FULL (cast, hook, minigame, recast).")
                .translation(key("default_mode"))
                .defineEnum("defaultMode", BotMode.OFF);
        ANNOUNCE_STATE_CHANGES = b.comment("Show an action-bar message when the bot is toggled, paused or stopped.")
                .translation(key("announce_state_changes"))
                .define("announceStateChanges", true);
        b.pop();

        b.comment("How the reel minigame is played.").translation(key("minigame")).push("minigame");
        COLLECT_TREASURE = b.comment("Hit treasure sweet-spots to fill the treasure bar.")
                .translation(key("collect_treasure"))
                .define("collectTreasure", true);
        TREASURE_PRIORITY = b.comment("While a treasure is still being collected, hold back fish hits that would finish the catch early.")
                .translation(key("treasure_priority"))
                .define("treasurePriority", true);
        TREASURE_HOLD_PERCENT = b.comment("With treasure priority on, fish progress is held below this fraction of the goal until the treasure is complete.")
                .translation(key("treasure_hold_percent"))
                .defineInRange("treasureHoldPercent", 0.85, 0.3, 0.99);
        AVOID_NEGATIVE_SPOTS = b.comment("Never hit sweet-spots whose reward is negative.")
                .translation(key("avoid_negative_spots"))
                .define("avoidNegativeSpots", true);
        HANDLE_MULTI_LAYER = b.comment("Automatically switch handle layers on multi-layer (A/D) minigames.")
                .translation(key("handle_multi_layer"))
                .define("handleMultiLayer", true);
        MIN_HIT_INTERVAL_MS = b.comment("Minimum milliseconds between two presses.")
                .translation(key("min_hit_interval_ms"))
                .defineInRange("minHitIntervalMs", 90, 0, 2000);
        HIT_INTERVAL_JITTER_MS = b.comment("Random extra milliseconds added after each press, for more human-looking timing.")
                .translation(key("hit_interval_jitter_ms"))
                .defineInRange("hitIntervalJitterMs", 40, 0, 1000);
        AIM_CENTER_BIAS = b.comment("0 = press as soon as the handle touches a sweet-spot, 0.9 = wait until it is near the centre. The bot always presses before the handle leaves the spot.")
                .translation(key("aim_center_bias"))
                .defineInRange("aimCenterBias", 0.35, 0.0, 0.9);
        SKIP_CHANCE = b.comment("Chance (0-1) to deliberately let a sweet-spot pass, imitating a late reaction. Never causes a miss penalty.")
                .translation(key("skip_chance"))
                .defineInRange("skipChance", 0.0, 0.0, 0.9);
        START_DELAY_MS = b.comment("Milliseconds to wait after the minigame opens before the first press.")
                .translation(key("start_delay_ms"))
                .defineInRange("startDelayMs", 350, 0, 5000);
        b.pop();

        b.comment("Casting, hooking and recasting in FULL mode.").translation(key("automation")).push("automation");
        HOOK_DELAY_MIN_TICKS = b.comment("Minimum reaction time in ticks before hooking a bite (20 ticks = 1 second). Bites expire after 80 ticks.")
                .translation(key("hook_delay_min_ticks"))
                .defineInRange("hookDelayMinTicks", 4, 0, 60);
        HOOK_DELAY_MAX_TICKS = b.comment("Maximum reaction time in ticks before hooking a bite.")
                .translation(key("hook_delay_max_ticks"))
                .defineInRange("hookDelayMaxTicks", 12, 0, 70);
        RECAST_DELAY_MIN_TICKS = b.comment("Minimum ticks to wait before casting again.")
                .translation(key("recast_delay_min_ticks"))
                .defineInRange("recastDelayMinTicks", 15, 2, 400);
        RECAST_DELAY_MAX_TICKS = b.comment("Maximum ticks to wait before casting again.")
                .translation(key("recast_delay_max_ticks"))
                .defineInRange("recastDelayMaxTicks", 35, 2, 600);
        BITE_TIMEOUT_SECONDS = b.comment("Reel in and recast if nothing bites for this many seconds. 0 disables.")
                .translation(key("bite_timeout_seconds"))
                .defineInRange("biteTimeoutSeconds", 90, 0, 3600);
        FLYING_TIMEOUT_SECONDS = b.comment("Reel in and recast if the bobber has not landed in liquid after this many seconds.")
                .translation(key("flying_timeout_seconds"))
                .defineInRange("flyingTimeoutSeconds", 6, 2, 120);
        AUTO_SELECT_ROD = b.comment("Switch to a Starcatcher rod from the hotbar when none is held, or when the held rod is too damaged.")
                .translation(key("auto_select_rod"))
                .define("autoSelectRod", true);
        b.pop();

        b.comment("Conditions that pause or stop FULL mode.").translation(key("safety")).push("safety");
        MIN_ROD_DURABILITY = b.comment("Never fish with a rod at or below this much durability left. 0 disables.")
                .translation(key("min_rod_durability"))
                .defineInRange("minRodDurability", 5, 0, 10000);
        STOP_WHEN_BAIT_EMPTY = b.comment("Stop when the rod's bait slot is empty.")
                .translation(key("stop_when_bait_empty"))
                .define("stopWhenBaitEmpty", false);
        STOP_WHEN_INVENTORY_FULL = b.comment("Stop when the inventory has no free slot.")
                .translation(key("stop_when_inventory_full"))
                .define("stopWhenInventoryFull", true);
        STOP_ON_DAMAGE = b.comment("Stop full-auto when you take damage.")
                .translation(key("stop_on_damage"))
                .define("stopOnDamage", true);
        MIN_HEALTH_PERCENT = b.comment("Stop full-auto when health falls below this fraction of max health. 0 disables.")
                .translation(key("min_health_percent"))
                .defineInRange("minHealthPercent", 0.5, 0.0, 1.0);
        HOSTILE_PAUSE_RADIUS = b.comment("Pause casting while a hostile mob is within this many blocks. 0 disables.")
                .translation(key("hostile_pause_radius"))
                .defineInRange("hostilePauseRadius", 8, 0, 64);
        MAX_CATCHES = b.comment("Stop after this many successful catches in one session. 0 = unlimited.")
                .translation(key("max_catches"))
                .defineInRange("maxCatches", 0, 0, 100000);
        MAX_SESSION_MINUTES = b.comment("Stop full-auto after this many minutes. 0 = unlimited.")
                .translation(key("max_session_minutes"))
                .defineInRange("maxSessionMinutes", 0, 0, 10080);
        b.pop();

        b.comment("Chat alerts, sounds and the catch log.").translation(key("notifications")).push("notifications");
        ALERT_RARITY = b.comment("Announce catches of this rarity or better in chat. NONE disables.")
                .translation(key("alert_rarity"))
                .defineEnum("alertRarity", Rarity.EPIC);
        ALERT_SOUND = b.comment("Play a sound with rarity alerts.")
                .translation(key("alert_sound"))
                .define("alertSound", true);
        CHAT_CATCH_SUMMARY = b.comment("Print a short chat line after every minigame.")
                .translation(key("chat_catch_summary"))
                .define("chatCatchSummary", false);
        LOG_CATCHES_TO_FILE = b.comment("Append every minigame result to <game dir>/scfishbot/catches.csv.")
                .translation(key("log_catches_to_file"))
                .define("logCatchesToFile", true);
        b.pop();

        b.comment("The on-screen status overlay.").translation(key("hud")).push("hud");
        HUD_ENABLED = b.comment("Show the status overlay.")
                .translation(key("hud_enabled"))
                .define("enabled", true);
        HUD_SHOW_WHEN_OFF = b.comment("Keep the overlay visible while the bot is OFF.")
                .translation(key("hud_show_when_off"))
                .define("showWhenOff", false);
        HUD_CORNER = b.comment("Screen corner for the overlay.")
                .translation(key("hud_corner"))
                .defineEnum("corner", HudCorner.TOP_LEFT);
        HUD_TOP_FISH_LINES = b.comment("How many of the most-caught items to list on the overlay.")
                .translation(key("hud_top_fish_lines"))
                .defineInRange("topFishLines", 3, 0, 10);
        b.pop();

        SPEC = b.build();
    }

    private static String key(String name)
    {
        return FishBotMod.MOD_ID + ".configuration." + name;
    }

    private FishBotConfig()
    {
    }
}
