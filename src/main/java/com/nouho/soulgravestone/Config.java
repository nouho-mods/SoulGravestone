package com.nouho.soulgravestone;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;


// Config class for Soul Gravestone mod
@EventBusSubscriber(modid = SoulGravestone.MODID, bus = EventBusSubscriber.Bus.MOD)
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // Start gravestone section
    static {
        BUILDER.push("gravestone");
    }

    // Configuration for XP retention ratio
    private static final ModConfigSpec.DoubleValue XP_RETAIN_RATIO = BUILDER
            .comment("Fraction of XP to keep in gravestone on death (0.0 = lose all, 1.0 = keep all)")
            .defineInRange("xpRetainRatio", 0.5, 0.0, 1.0);

    // Configuration for gravestone placement in water
    private static final ModConfigSpec.BooleanValue ALLOW_GRAVESTONE_IN_WATER = BUILDER
            .comment("If true, gravestone can be created in water\nIf false, use last safe position")
            .define("allowGravestoneInWater", true);

    // Configuration for gravestone placement in lava
    private static final ModConfigSpec.BooleanValue GRAVESTONE_ON_LAVA_DEATH = BUILDER
            .comment("If true, gravestone can be created when a player dies in lava\nIf false, the items drop normally")
            .define("gravestoneOnLavaDeath", true);
    private static final ModConfigSpec.BooleanValue ALLOW_GRAVESTONE_IN_LAVA = BUILDER
            .comment("If true, place gravestone in the lava\nIf false, use last safe position")
            .define("allowGravestoneInLava", false);

    // Configuration for gravestone placement in the void
    private static final ModConfigSpec.BooleanValue GRAVESTONE_ON_VOID_DEATH = BUILDER
            .comment("If true, dying to the void will place a gravestone at the last safe position\nIf false, the items drop normally")
            .define("gravestoneOnVoidDeath", true);

    // End gravestone section, start soul shape section
    static {
        BUILDER.pop();
        BUILDER.push("soulShape");
    }

    // Configuration for Soul Shape effect duration (in minutes)
    public static final ModConfigSpec.DoubleValue SOUL_SHAPE_DURATION_MINUTES = BUILDER
        .comment("Duration of the Soul Shape effect after respawn in minutes (0.0 = disabled, maximum 60.0)")
        .defineInRange("soulShapeDurationMinutes", 20.0, 0.0, 60.0);

    // Configuration for Soul Shape speed bonus
    private static final ModConfigSpec.DoubleValue SOUL_SHAPE_SPEED_BONUS = BUILDER
        .comment("Speed bonus multiplier for Soul Shape effect (0.0 = no bonus, 0.2 = 20% faster, 1.0 = 100% faster)")
        .defineInRange("soulShapeSpeedBonus", 0.2, 0.0, 2.0);

    // Configuration for Soul Shape invincibility
    private static final ModConfigSpec.BooleanValue SOUL_SHAPE_INVINCIBLE = BUILDER
        .comment("If true, players with Soul Shape effect are invincible")
        .define("soulShapeInvincible", true);

    // Configuration for Soul Shape slow falling
    private static final ModConfigSpec.BooleanValue SOUL_SHAPE_SLOW_FALLING = BUILDER
        .comment("If true, players with Soul Shape effect have slow falling")
        .define("soulShapeSlowFalling", true);

    // Configuration for Soul Shape attack prevention
    private static final ModConfigSpec.BooleanValue SOUL_SHAPE_PREVENT_ATTACKS = BUILDER
        .comment("If true, players with Soul Shape effect cannot attack mobs or other players")
        .define("soulShapePreventAttacks", true);

    // Configuration for Soul Shape block breaking prevention
    private static final ModConfigSpec.BooleanValue SOUL_SHAPE_PREVENT_BLOCK_BREAKING = BUILDER
        .comment("If true, players with Soul Shape effect cannot break blocks")
        .define("soulShapePreventBlockBreaking", false);

    // Configuration for Soul Shape effect removal on block breaking
    private static final ModConfigSpec.BooleanValue SOUL_SHAPE_REMOVE_ON_BLOCK_BREAK = BUILDER
        .comment("If true, breaking a block while Soul Shape is active will remove the effect (only when soulShapePreventBlockBreaking is false)")
        .define("soulShapeRemoveOnBlockBreak", true);

    // Configuration for Soul Shape effect duration after item retrieval (in seconds)
    private static final ModConfigSpec.DoubleValue SOUL_SHAPE_RETRIEVE_DURATION_SECONDS = BUILDER
        .comment("Duration of the Soul Shape effect after retrieving items from gravestone in seconds (0.0 = disabled, maximum 300.0)")
        .defineInRange("soulShapeRetrieveDurationSeconds", 5.0, 0.0, 300.0);

    // End soul shape section
    static {
        BUILDER.pop();
    }

    static final ModConfigSpec SPEC = BUILDER.build();

    public static double xpRetainRatio;
    public static boolean allowGravestoneInWater;
    public static boolean gravestoneOnLavaDeath;
    public static boolean allowGravestoneInLava;
    public static boolean gravestoneOnVoidDeath;
    public static double soulShapeDurationMinutes;
    public static int soulShapeDurationTicks;
    public static double soulShapeSpeedBonus;
    public static boolean soulShapeInvincible;
    public static boolean soulShapeSlowFalling;
    public static boolean soulShapePreventAttacks;
    public static boolean soulShapePreventBlockBreaking;
    public static boolean soulShapeRemoveOnBlockBreak;
    public static double soulShapeRetrieveDurationSeconds;
    public static int soulShapeRetrieveDurationTicks;

    // Load configuration values
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        xpRetainRatio = XP_RETAIN_RATIO.get();
        allowGravestoneInWater = ALLOW_GRAVESTONE_IN_WATER.get();
        gravestoneOnLavaDeath = GRAVESTONE_ON_LAVA_DEATH.get();
        allowGravestoneInLava = ALLOW_GRAVESTONE_IN_LAVA.get();
        gravestoneOnVoidDeath = GRAVESTONE_ON_VOID_DEATH.get();
        soulShapeDurationMinutes = SOUL_SHAPE_DURATION_MINUTES.get();
        soulShapeDurationTicks = (int)(soulShapeDurationMinutes * 1200.0);
        soulShapeSpeedBonus = SOUL_SHAPE_SPEED_BONUS.get();
        soulShapeInvincible = SOUL_SHAPE_INVINCIBLE.get();
        soulShapeSlowFalling = SOUL_SHAPE_SLOW_FALLING.get();
        soulShapePreventAttacks = SOUL_SHAPE_PREVENT_ATTACKS.get();
        soulShapePreventBlockBreaking = SOUL_SHAPE_PREVENT_BLOCK_BREAKING.get();
        soulShapeRemoveOnBlockBreak = SOUL_SHAPE_REMOVE_ON_BLOCK_BREAK.get();
        soulShapeRetrieveDurationSeconds = SOUL_SHAPE_RETRIEVE_DURATION_SECONDS.get();
        soulShapeRetrieveDurationTicks = (int)(soulShapeRetrieveDurationSeconds * 20.0);
    }
}
