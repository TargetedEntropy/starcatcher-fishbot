package com.targetedentropy.scfishbot;

import com.wdiscute.starcatcher.minigame.ActiveSweetSpot;
import com.wdiscute.starcatcher.minigame.FishingMinigameScreen;
import com.wdiscute.starcatcher.minigame.PartialTickHelper;
import com.wdiscute.starcatcher.modifiers.minigamemodifiers.AbstractMinigameModifier;
import com.wdiscute.starcatcher.modifiers.minigamemodifiers.Nikdo53Modifier;
import com.wdiscute.starcatcher.registry.sweetspotbehaviour.TreasureSweetSpotBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;

/**
 * Plays Starcatcher's reel minigame.
 * <p>
 * Every frame and every tick the solver rebuilds the exact hit test that
 * {@link FishingMinigameScreen#inputPressed()} performs (same handle position, same leeway, same
 * iteration order, same modifier vetoes) and only presses when that test lands on a spot worth
 * hitting. A press therefore never counts as a miss, so catches stay perfect unless
 * {@code skipChance} or the configured timing makes the bot let a spot pass.
 */
public final class MinigameSolver
{
    /** Key Starcatcher's multi-layer modifier uses in {@link ActiveSweetSpot#extraData}. */
    private static final int NIKDO_LAYER_KEY = 53;

    private final RandomSource random = RandomSource.create();

    private @Nullable FishingMinigameScreen current;
    private long openedAtNanos;
    private long nextPressAllowedNanos;

    /** Spots the bot decided to let pass this time around (skip chance). */
    private final Set<ActiveSweetSpot> skipped = Collections.newSetFromMap(new IdentityHashMap<>());
    /** Per-spot random aim depth so presses do not always land at the same offset. */
    private final Map<ActiveSweetSpot, Float> aimDepth = new IdentityHashMap<>();

    private int pressesThisGame;

    public void reset()
    {
        current = null;
        skipped.clear();
        aimDepth.clear();
        pressesThisGame = 0;
    }

    public int pressesThisGame()
    {
        return pressesThisGame;
    }

    /**
     * Called every rendered frame and every client tick while a minigame screen is open.
     */
    public void update(FishingMinigameScreen screen)
    {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (screen.isSettingsScreen()) return;

        long now = System.nanoTime();

        if (screen != current)
        {
            reset();
            current = screen;
            openedAtNanos = now;
            nextPressAllowedNanos = now + FishBotConfig.START_DELAY_MS.get() * 1_000_000L;
        }

        if (now < nextPressAllowedNanos) return;

        // Refresh the interpolation value exactly the way the screen does when it renders, so
        // getHandlePosPrecise() reflects where the handle is at this instant.
        screen.partial = PartialTickHelper.INSTANCE.getPartialTicks(mc.level);

        List<ActiveSweetSpot> spots = screen.getActiveSweetSpots();
        if (spots.isEmpty()) return;

        float handle = screen.getHandlePosPrecise();
        forgetSpotsHandleLeft(handle);

        Nikdo53Modifier layers = findLayerModifier(screen);
        if (layers != null && FishBotConfig.HANDLE_MULTI_LAYER.get())
            alignLayer(screen, layers, spots, handle);

        ActiveSweetSpot target = spotThatWouldBeHit(screen, spots, handle);
        if (target == null) return;
        if (!isWorthHitting(screen, spots, target)) return;
        if (skipped.contains(target)) return;
        if (!aimedWellEnough(screen, target, handle)) return;

        double skipChance = FishBotConfig.SKIP_CHANCE.get();
        if (skipChance > 0 && random.nextDouble() < skipChance)
        {
            skipped.add(target);
            return;
        }

        screen.inputPressed();
        pressesThisGame++;
        aimDepth.remove(target);

        long interval = FishBotConfig.MIN_HIT_INTERVAL_MS.get();
        int jitter = FishBotConfig.HIT_INTERVAL_JITTER_MS.get();
        if (jitter > 0) interval += random.nextInt(jitter + 1);
        nextPressAllowedNanos = now + interval * 1_000_000L;
    }

    /**
     * Mirrors the loop in {@link FishingMinigameScreen#inputPressed()}: the newest spot that the
     * handle overlaps, that can be hit, and that no modifier vetoes.
     */
    private static @Nullable ActiveSweetSpot spotThatWouldBeHit(FishingMinigameScreen screen, List<ActiveSweetSpot> spots, float handle)
    {
        List<AbstractMinigameModifier> modifiers = screen.getModifiers();
        for (ActiveSweetSpot spot : spots.reversed())
        {
            if (!spot.canHit) continue;
            if (!FishingMinigameScreen.doDegreesOverlapWithLeeway(handle, spot.pos, spot.thickness / 2)) continue;

            boolean allowed = true;
            for (AbstractMinigameModifier modifier : modifiers)
            {
                if (!modifier.canHitSpot(screen, spot))
                {
                    allowed = false;
                    break;
                }
            }
            if (allowed) return spot;
        }
        return null;
    }

    private static boolean isWorthHitting(FishingMinigameScreen screen, List<ActiveSweetSpot> spots, ActiveSweetSpot spot)
    {
        if (spot.removed || spot.behaviour == null) return false;

        if (spot.behaviour instanceof TreasureSweetSpotBehaviour)
            return FishBotConfig.COLLECT_TREASURE.get() && screen.treasureProgress < 100;

        if (spot.reward < 0 && FishBotConfig.AVOID_NEGATIVE_SPOTS.get()) return false;

        // Hold the fish just short of the finish line while the treasure is still collectable.
        if (FishBotConfig.COLLECT_TREASURE.get()
            && FishBotConfig.TREASURE_PRIORITY.get()
            && screen.treasureActive
            && screen.treasureProgress <= 100
            && spot.reward > 0
            && hasLiveTreasureSpot(spots))
        {
            float holdAt = (float) (screen.hp * FishBotConfig.TREASURE_HOLD_PERCENT.get());
            if (screen.progress + spot.reward >= holdAt) return false;
        }

        return true;
    }

    private static boolean hasLiveTreasureSpot(List<ActiveSweetSpot> spots)
    {
        for (ActiveSweetSpot spot : spots)
            if (!spot.removed && spot.canHit && spot.behaviour instanceof TreasureSweetSpotBehaviour)
                return true;
        return false;
    }

    /**
     * Waits for the handle to get some way into the spot before pressing. Presses straight away
     * once the handle has passed the spot's centre (it will only get further away from here on),
     * or when the handle is not moving at all.
     */
    private boolean aimedWellEnough(FishingMinigameScreen screen, ActiveSweetSpot spot, float handle)
    {
        double bias = FishBotConfig.AIM_CENTER_BIAS.get();
        if (bias <= 0) return true;

        float direction = Math.signum(screen.handleSpeed * screen.currentRotation);
        if (direction == 0) return true;

        float leeway = spot.thickness / 2f;
        float distance = circularDistance(handle, spot.pos);

        float depth = aimDepth.computeIfAbsent(spot, s -> (float) (bias * (0.5 + random.nextDouble() * 0.5)));
        if (distance <= leeway * (1f - depth)) return true;

        // Signed offset of the handle from the centre along its direction of travel.
        float offset = wrapSigned((handle - spot.pos) * direction);
        return offset >= 0;
    }

    private static float wrapSigned(float degrees)
    {
        float d = ((degrees % 360f) + 360f) % 360f;
        return d > 180f ? d - 360f : d;
    }

    private void forgetSpotsHandleLeft(float handle)
    {
        aimDepth.keySet().removeIf(spot -> spot.removed || !overlaps(spot, handle));
        skipped.removeIf(spot -> spot.removed || !overlaps(spot, handle));
    }

    private static boolean overlaps(ActiveSweetSpot spot, float handle)
    {
        return FishingMinigameScreen.doDegreesOverlapWithLeeway(handle, spot.pos, spot.thickness / 2);
    }

    private static @Nullable Nikdo53Modifier findLayerModifier(FishingMinigameScreen screen)
    {
        for (AbstractMinigameModifier modifier : screen.getModifiers())
            if (modifier instanceof Nikdo53Modifier layers && !layers.removed)
                return layers;
        return null;
    }

    /**
     * Multi-layer minigames only accept hits on spots in the handle's current layer. Switch the
     * handle to the layer of the next worthwhile spot it will sweep over, like pressing A/D.
     */
    private static void alignLayer(FishingMinigameScreen screen, Nikdo53Modifier layers, List<ActiveSweetSpot> spots, float handle)
    {
        float direction = Math.signum(screen.handleSpeed * screen.currentRotation);
        if (direction == 0) direction = 1;

        ActiveSweetSpot best = null;
        float bestAhead = Float.MAX_VALUE;

        for (ActiveSweetSpot spot : spots)
        {
            if (spot.removed || !spot.canHit || spot.behaviour == null) continue;
            if (!(spot.extraData.get(NIKDO_LAYER_KEY) instanceof Integer)) continue;
            if (!isWorthHitting(screen, spots, spot)) continue;

            float ahead;
            if (overlaps(spot, handle))
                ahead = 0;
            else
            {
                ahead = (spot.pos - handle) * direction;
                ahead = ((ahead % 360f) + 360f) % 360f;
            }

            if (ahead < bestAhead)
            {
                bestAhead = ahead;
                best = spot;
            }
        }

        if (best != null)
        {
            int layer = (Integer) best.extraData.get(NIKDO_LAYER_KEY);
            layers.handleLayer = Math.clamp(layer, 0, layers.maxHandleLayer);
        }
    }

    static float circularDistance(float a, float b)
    {
        float d = Math.abs(a - b) % 360f;
        return Math.min(d, 360f - d);
    }

    public long millisSinceOpened()
    {
        return current == null ? 0 : (System.nanoTime() - openedAtNanos) / 1_000_000L;
    }
}
