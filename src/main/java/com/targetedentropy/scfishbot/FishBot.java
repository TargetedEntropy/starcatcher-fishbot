package com.targetedentropy.scfishbot;

import com.wdiscute.starcatcher.SCTags;
import com.wdiscute.starcatcher.bobentity.FishingBobEntity;
import com.wdiscute.starcatcher.fish.Rarity;
import com.wdiscute.starcatcher.minigame.FishingMinigameScreen;
import com.wdiscute.starcatcher.modifiers.minigamemodifiers.AbstractMinigameModifier;
import com.wdiscute.starcatcher.registry.SCDataComponents;
import com.wdiscute.utils.MaybeStack;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The bot's brain. Driven by client ticks (automation) and render frames (minigame timing).
 */
public final class FishBot
{
    public static final FishBot INSTANCE = new FishBot();

    /** Values of {@link FishingBobEntity#STATE}. */
    private static final int BOB_FLYING = 1;
    private static final int BOB_BOBBING = 2;
    private static final int BOB_BITING = 3;
    private static final int BOB_FISHING = 4;

    /** Starcatcher lets a bite go after 80 ticks; always hook before that. */
    private static final int BITE_WINDOW_TICKS = 75;
    /** How long to wait for the bobber entity to show up client-side after casting. */
    private static final int CAST_CONFIRM_TICKS = 40;
    /** How long a hooked bobber may sit without a minigame opening before the bot reels in. */
    private static final int MINIGAME_OPEN_TIMEOUT_TICKS = 100;

    public enum Status
    {
        OFF, WAITING_FOR_MINIGAME, PLAYING_MINIGAME, PAUSED, COOLDOWN, CASTING, WAITING_FOR_BITE, HOOKING, REELING_IN, STOPPED
    }

    private final RandomSource random = RandomSource.create();
    private final MinigameSolver solver = new MinigameSolver();
    private final SessionStats stats = new SessionStats();

    private BotMode mode = BotMode.OFF;
    private Status status = Status.OFF;
    private Component statusDetail = Component.empty();

    private @Nullable FishingMinigameScreen activeMinigame;

    private int actionCooldown;
    private int castConfirmTicks;
    private int lastBobState = -1;
    private int bobStateTicks;
    private int hookDelay;
    private long fullAutoStartedAt;
    private boolean wasHurt;
    private long stoppedAtMillis;

    private FishBot()
    {
    }

    public BotMode mode()
    {
        return mode;
    }

    public Status status()
    {
        return status;
    }

    public Component statusDetail()
    {
        return statusDetail;
    }

    /** True for a short while after a safety stop, so the overlay can show why the bot stopped. */
    public boolean recentlyStopped()
    {
        return status == Status.STOPPED && System.currentTimeMillis() - stoppedAtMillis < 15_000;
    }

    public SessionStats stats()
    {
        return stats;
    }

    public long fullAutoMillis()
    {
        return mode == BotMode.FULL ? System.currentTimeMillis() - fullAutoStartedAt : 0;
    }

    // ---------------------------------------------------------------- mode control

    public void setMode(BotMode newMode)
    {
        setMode(newMode, null);
    }

    public void setMode(BotMode newMode, @Nullable Component reason)
    {
        BotMode old = mode;
        mode = newMode;

        if (newMode == BotMode.FULL && old != BotMode.FULL)
        {
            fullAutoStartedAt = System.currentTimeMillis();
            actionCooldown = 5;
            castConfirmTicks = 0;
            lastBobState = -1;
            LocalPlayer player = Minecraft.getInstance().player;
            wasHurt = player != null && player.hurtTime > 0;
        }

        if (newMode == BotMode.OFF)
        {
            stoppedAtMillis = System.currentTimeMillis();
            status = reason == null ? Status.OFF : Status.STOPPED;
            statusDetail = reason == null ? Component.empty() : reason;
        }

        if (old != newMode || reason != null) announce(newMode, reason);
    }

    public void toggle(BotMode target)
    {
        setMode(mode == target ? BotMode.OFF : target);
    }

    private void stop(Component reason)
    {
        setMode(BotMode.OFF, reason);
        playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f);
    }

    private void announce(BotMode newMode, @Nullable Component reason)
    {
        if (!FishBotConfig.ANNOUNCE_STATE_CHANGES.get()) return;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        MutableComponent msg = Component.translatable("scfishbot.message.mode", newMode.displayName());
        if (reason != null) msg.append(Component.literal(" - ").withStyle(ChatFormatting.GRAY)).append(reason.copy().withStyle(ChatFormatting.YELLOW));
        player.sendOverlayMessage(msg);
    }

    public void onLevelLeft()
    {
        mode = BotMode.OFF;
        activeMinigame = null;
        solver.reset();
        status = Status.OFF;
    }

    public void onLevelJoined()
    {
        mode = BotMode.OFF;
        BotMode initial = FishBotConfig.DEFAULT_MODE.get();
        if (initial != BotMode.OFF) setMode(initial);
    }

    // ---------------------------------------------------------------- per-frame

    /** Called every rendered frame for fine-grained minigame timing. */
    public void onFrame()
    {
        if (!mode.solvesMinigame()) return;
        if (Minecraft.getInstance().screen instanceof FishingMinigameScreen minigame && minigame == activeMinigame)
            solver.update(minigame);
    }

    // ---------------------------------------------------------------- per-tick

    public void onClientTick()
    {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || mc.gameMode == null) return;

        Screen screen = mc.screen;

        // Minigame lifecycle: notice when one opens and when it closes.
        if (screen instanceof FishingMinigameScreen minigame && !minigame.isSettingsScreen())
        {
            if (activeMinigame != minigame)
            {
                if (activeMinigame != null) finishMinigame(activeMinigame);
                activeMinigame = minigame;
                solver.reset();
            }
        }
        else if (activeMinigame != null)
        {
            finishMinigame(activeMinigame);
            activeMinigame = null;
            solver.reset();
            actionCooldown = Math.max(actionCooldown, recastDelay());
            castConfirmTicks = 0;
        }

        if (mode == BotMode.OFF)
        {
            if (status != Status.STOPPED) status = Status.OFF;
            return;
        }

        if (activeMinigame != null)
        {
            setStatus(Status.PLAYING_MINIGAME, Component.translatable("scfishbot.status.detail.minigame",
                    Math.round(activeMinigame.progress), activeMinigame.hp));
            solver.update(activeMinigame);
            return;
        }

        if (mode == BotMode.ASSIST)
        {
            setStatus(Status.WAITING_FOR_MINIGAME, Component.empty());
            return;
        }

        if (screen != null)
        {
            setStatus(Status.PAUSED, Component.translatable("scfishbot.status.detail.menu_open"));
            return;
        }

        fullAutoTick(mc, player);
    }

    private void fullAutoTick(Minecraft mc, LocalPlayer player)
    {
        if (actionCooldown > 0) actionCooldown--;

        // ---- session limits
        int maxCatches = FishBotConfig.MAX_CATCHES.get();
        if (maxCatches > 0 && stats.caught >= maxCatches)
        {
            stop(Component.translatable("scfishbot.stop.max_catches", maxCatches));
            return;
        }
        int maxMinutes = FishBotConfig.MAX_SESSION_MINUTES.get();
        if (maxMinutes > 0 && fullAutoMillis() >= maxMinutes * 60_000L)
        {
            stop(Component.translatable("scfishbot.stop.max_time", maxMinutes));
            return;
        }

        // ---- player safety
        boolean hurtNow = player.hurtTime > 0;
        if (FishBotConfig.STOP_ON_DAMAGE.get() && hurtNow && !wasHurt)
        {
            wasHurt = true;
            stop(Component.translatable("scfishbot.stop.damage"));
            return;
        }
        wasHurt = hurtNow;

        double minHealth = FishBotConfig.MIN_HEALTH_PERCENT.get();
        if (minHealth > 0 && player.getHealth() < player.getMaxHealth() * minHealth)
        {
            stop(Component.translatable("scfishbot.stop.low_health"));
            return;
        }

        if (player.isDeadOrDying())
        {
            stop(Component.translatable("scfishbot.stop.died"));
            return;
        }

        FishingBobEntity bob = findOwnBob(mc, player);

        // ---- the rod
        InteractionHand hand = rodHand(player);
        if (bob == null && (hand == null || !rodUsable(player.getItemInHand(hand))))
        {
            if (FishBotConfig.AUTO_SELECT_ROD.get() && selectRodFromHotbar(player))
            {
                stats.rodSwaps++;
                actionCooldown = Math.max(actionCooldown, 4);
                setStatus(Status.COOLDOWN, Component.translatable("scfishbot.status.detail.switching_rod"));
                return;
            }
            stop(Component.translatable(hand == null ? "scfishbot.stop.no_rod" : "scfishbot.stop.rod_durability"));
            return;
        }
        if (hand == null)
        {
            // Rod was put away while the bobber is still out: Starcatcher will discard the bobber itself.
            setStatus(Status.PAUSED, Component.translatable("scfishbot.status.detail.no_rod"));
            return;
        }

        if (bob == null)
        {
            castOrWait(mc, player, hand);
            return;
        }

        castConfirmTicks = 0;
        int state = bob.getEntityData().get(FishingBobEntity.STATE);
        if (state != lastBobState)
        {
            lastBobState = state;
            bobStateTicks = 0;
            if (state == BOB_BITING)
            {
                stats.bites++;
                int min = FishBotConfig.HOOK_DELAY_MIN_TICKS.get();
                int max = Math.max(min, FishBotConfig.HOOK_DELAY_MAX_TICKS.get());
                hookDelay = Math.min(BITE_WINDOW_TICKS, min + random.nextInt(max - min + 1));
            }
        }
        else
        {
            bobStateTicks++;
        }

        switch (state)
        {
            case BOB_BITING ->
            {
                setStatus(Status.HOOKING, Component.empty());
                if (bobStateTicks >= hookDelay && actionCooldown == 0)
                {
                    if (useRod(mc, player, hand))
                    {
                        stats.hooks++;
                        actionCooldown = 10;
                    }
                }
            }
            case BOB_BOBBING ->
            {
                int timeout = FishBotConfig.BITE_TIMEOUT_SECONDS.get();
                setStatus(Status.WAITING_FOR_BITE, Component.translatable("scfishbot.status.detail.seconds", bobStateTicks / 20));
                if (timeout > 0 && bobStateTicks > timeout * 20) reelIn(mc, player, hand);
            }
            case BOB_FISHING ->
            {
                setStatus(Status.WAITING_FOR_MINIGAME, Component.empty());
                if (bobStateTicks > MINIGAME_OPEN_TIMEOUT_TICKS) reelIn(mc, player, hand);
            }
            default ->
            {
                setStatus(Status.CASTING, Component.empty());
                if (bobStateTicks > FishBotConfig.FLYING_TIMEOUT_SECONDS.get() * 20) reelIn(mc, player, hand);
            }
        }
    }

    private void castOrWait(Minecraft mc, LocalPlayer player, InteractionHand hand)
    {
        lastBobState = -1;

        // We cast recently and the server has not shown us the bobber yet: casting again would reel it in.
        if (castConfirmTicks > 0)
        {
            castConfirmTicks--;
            setStatus(Status.CASTING, Component.empty());
            return;
        }

        if (FishBotConfig.STOP_WHEN_INVENTORY_FULL.get() && player.getInventory().getFreeSlot() == -1)
        {
            stop(Component.translatable("scfishbot.stop.inventory_full"));
            return;
        }

        if (FishBotConfig.STOP_WHEN_BAIT_EMPTY.get() && baitEmpty(player.getItemInHand(hand)))
        {
            stop(Component.translatable("scfishbot.stop.no_bait"));
            return;
        }

        int radius = FishBotConfig.HOSTILE_PAUSE_RADIUS.get();
        if (radius > 0 && hostileNearby(mc, player, radius))
        {
            setStatus(Status.PAUSED, Component.translatable("scfishbot.status.detail.hostile"));
            return;
        }

        if (actionCooldown > 0)
        {
            setStatus(Status.COOLDOWN, Component.translatable("scfishbot.status.detail.seconds_left", (actionCooldown + 19) / 20));
            return;
        }

        if (useRod(mc, player, hand))
        {
            stats.casts++;
            castConfirmTicks = CAST_CONFIRM_TICKS;
            actionCooldown = 10;
            setStatus(Status.CASTING, Component.empty());
        }
    }

    private void reelIn(Minecraft mc, LocalPlayer player, InteractionHand hand)
    {
        if (actionCooldown > 0) return;
        setStatus(Status.REELING_IN, Component.empty());
        useRod(mc, player, hand);
        lastBobState = -1;
        actionCooldown = recastDelay();
    }

    private int recastDelay()
    {
        int min = FishBotConfig.RECAST_DELAY_MIN_TICKS.get();
        int max = Math.max(min, FishBotConfig.RECAST_DELAY_MAX_TICKS.get());
        return min + random.nextInt(max - min + 1);
    }

    /** Right-clicks the rod exactly like the vanilla use key does. */
    private static boolean useRod(Minecraft mc, LocalPlayer player, InteractionHand hand)
    {
        if (mc.gameMode == null || player.isUsingItem()) return false;

        InteractionResult result = mc.gameMode.useItem(player, hand);
        if (result instanceof InteractionResult.Success success)
        {
            if (success.swingSource() == InteractionResult.SwingSource.CLIENT) player.swing(hand);
            mc.gameRenderer.itemInHandRenderer.itemUsed(hand);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- minigame results

    private void finishMinigame(FishingMinigameScreen game)
    {
        if (mode == BotMode.OFF && solver.pressesThisGame() == 0) return;

        boolean success = game.progressSmooth > game.hp;
        boolean treasure = success && (game.treasureProgress > 100 || forcesTreasure(game));
        boolean perfect = success && game.perfectCatch;
        Rarity rarity = game.rarity == null ? Rarity.NONE : game.rarity;

        SessionStats.MinigameResult result = SessionStats.MinigameResult.of(success, game.itemBeingFished, rarity,
                perfect, treasure, game.tickCount, game.consecutiveHits, solver.pressesThisGame());
        stats.recordMinigame(result, mode);

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return;

        if (FishBotConfig.CHAT_CATCH_SUMMARY.get())
        {
            MutableComponent line = Component.literal("[FishBot] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.translatable(success ? "scfishbot.message.caught" : "scfishbot.message.lost",
                            game.itemBeingFished.getHoverName()).withStyle(success ? ChatFormatting.GREEN : ChatFormatting.RED));
            if (perfect) line.append(Component.translatable("scfishbot.message.perfect").withStyle(ChatFormatting.GOLD));
            if (treasure) line.append(Component.translatable("scfishbot.message.treasure").withStyle(ChatFormatting.YELLOW));
            line.append(Component.literal(String.format(" (%.1fs)", game.tickCount / 20f)).withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(line);
        }

        Rarity alertAt = FishBotConfig.ALERT_RARITY.get();
        if (success && alertAt != Rarity.NONE && rarity != Rarity.TRASH && rarity.ordinal() >= alertAt.ordinal())
        {
            player.sendSystemMessage(Component.literal("[FishBot] ").withStyle(ChatFormatting.DARK_AQUA)
                    .append(Component.translatable("scfishbot.message.rare_catch",
                            Component.literal(rarity.getSerializedName().toUpperCase()).withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.BOLD),
                            game.itemBeingFished.getHoverName())));
            if (FishBotConfig.ALERT_SOUND.get()) playSound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f);
        }
    }

    private static boolean forcesTreasure(FishingMinigameScreen game)
    {
        try
        {
            for (AbstractMinigameModifier modifier : game.getModifiers())
                if (modifier.forceAwardTreasure(game)) return true;
        }
        catch (RuntimeException ignored)
        {
            // Modifiers have already been torn down; treat as no forced treasure.
        }
        return false;
    }

    // ---------------------------------------------------------------- world helpers

    private static @Nullable FishingBobEntity findOwnBob(Minecraft mc, LocalPlayer player)
    {
        if (mc.level == null) return null;
        for (Entity entity : mc.level.entitiesForRendering())
        {
            if (entity instanceof FishingBobEntity bob && !bob.isRemoved() && bob.getOwner() == player)
                return bob;
        }
        return null;
    }

    private static @Nullable InteractionHand rodHand(LocalPlayer player)
    {
        if (player.getMainHandItem().is(SCTags.RODS)) return InteractionHand.MAIN_HAND;
        if (player.getOffhandItem().is(SCTags.RODS)) return InteractionHand.OFF_HAND;
        return null;
    }

    private static boolean rodUsable(ItemStack rod)
    {
        if (!rod.is(SCTags.RODS)) return false;
        int min = FishBotConfig.MIN_ROD_DURABILITY.get();
        if (min <= 0 || !rod.isDamageableItem()) return true;
        return rod.getMaxDamage() - rod.getDamageValue() > min;
    }

    /** Picks the hotbar rod with the most durability left. */
    private static boolean selectRodFromHotbar(LocalPlayer player)
    {
        Inventory inventory = player.getInventory();
        int bestSlot = -1;
        int bestDurability = -1;
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (!rodUsable(stack)) continue;
            int durability = stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
            if (durability > bestDurability)
            {
                bestDurability = durability;
                bestSlot = slot;
            }
        }
        if (bestSlot < 0 || bestSlot == inventory.getSelectedSlot()) return false;
        inventory.setSelectedSlot(bestSlot);
        return true;
    }

    private static boolean baitEmpty(ItemStack rod)
    {
        MaybeStack bait = SCDataComponents.getOrDefault(rod, SCDataComponents.BAIT, MaybeStack.EMPTY);
        return bait.toStack().isEmpty();
    }

    private static boolean hostileNearby(Minecraft mc, LocalPlayer player, int radius)
    {
        if (mc.level == null) return false;
        return !mc.level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e instanceof Enemy && e.isAlive() && e != player).isEmpty();
    }

    private void setStatus(Status newStatus, Component detail)
    {
        status = newStatus;
        statusDetail = detail;
    }

    private static void playSound(net.minecraft.sounds.SoundEvent sound, float pitch)
    {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
    }
}
