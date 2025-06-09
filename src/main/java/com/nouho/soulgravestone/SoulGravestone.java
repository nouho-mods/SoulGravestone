package com.nouho.soulgravestone;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.nouho.soulgravestone.gravestone.GravestoneBlock;
import com.nouho.soulgravestone.gravestone.GravestoneBlockEntity;
import com.nouho.soulgravestone.soulshape.SoulShapeEffect;

// The value here should match an entry in the META-INF/neoforge.mods.toml file
@Mod(SoulGravestone.MODID)
public class SoulGravestone
{
    // Define mod id in a common place for everything to reference
    public static final String MODID = "soulgravestone";
    // Directly reference a slf4j logger
    public static final Logger LOGGER = LogUtils.getLogger(); // Make public
    // Create a Deferred Register to hold Blocks which will all be registered under the "soulgravestone" namespace
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    // Create a Deferred Register to hold Items which will all be registered under the "soulgravestone" namespace
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    // Create a Deferred Register to hold BlockEntityTypes which will all be registered under the "soulgravestone" namespace
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    // Create a Deferred Register to hold CreativeModeTabs which will all be registered under the "soulgravestone" namespace
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    // Create a Deferred Register to hold MobEffects which will all be registered under the "soulgravestone" namespace
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(Registries.MOB_EFFECT, MODID);
    // Register the SoulShapeEffect and return a Holder<MobEffect>
    public static final DeferredHolder<MobEffect, MobEffect> SOUL_SHAPE_EFFECT = EFFECTS.register(
        "soul_shape",
        () -> new SoulShapeEffect()
    );

    // Register the gravestone block as a block with a block entity
    public static final DeferredBlock<GravestoneBlock> GRAVESTONE_BLOCK = BLOCKS.register(
        "gravestone",
        () -> new GravestoneBlock(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0f, 3600000.0f))
    );
    // Register the gravestone block item
    public static final DeferredItem<BlockItem> GRAVESTONE_BLOCK_ITEM = ITEMS.registerSimpleBlockItem(
        "gravestone",
        GRAVESTONE_BLOCK
    );

    // Register the gravestone block entity type
    @SuppressWarnings("null")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<GravestoneBlockEntity>> GRAVESTONE_BLOCK_ENTITY = BLOCK_ENTITIES.register(
        "gravestone",
        () -> BlockEntityType.Builder.of(GravestoneBlockEntity::new, GRAVESTONE_BLOCK.get()).build(null)
    );

    // Register the creative tab for the mod
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> GRAVESTONE_TAB = CREATIVE_MODE_TABS.register("gravestone_tab", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.soulgravestone"))
        .icon(() -> GRAVESTONE_BLOCK_ITEM.get().getDefaultInstance())
        .displayItems((parameters, output) -> {
            output.accept(GRAVESTONE_BLOCK_ITEM.get()); // Only add once here
        }).build());

    // Track gravestone positions for each player
    private static final Map<UUID, BlockPos> lastGravestonePositions = new HashMap<>();
    private static final Map<UUID, Level> lastGravestoneLevels = new HashMap<>();

    // The constructor for the mod class is the first code that is run when your mod is loaded.
    // FML will recognize some parameter types like IEventBus or ModContainer and pass them in automatically.
    public SoulGravestone(IEventBus modEventBus, ModContainer modContainer)
    {
        // Register the Deferred Register to the mod event bus so blocks get registered
        BLOCKS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so items get registered
        ITEMS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so block entities get registered
        BLOCK_ENTITIES.register(modEventBus);
        // Register the Deferred Register to the mod event bus so tabs get registered
        CREATIVE_MODE_TABS.register(modEventBus);
        // Register the Deferred Register to the mod event bus so effects get registered
        EFFECTS.register(modEventBus);

        // Register event handlers for gameplay features
        NeoForge.EVENT_BUS.register(new com.nouho.soulgravestone.events.DeathEvents());
        NeoForge.EVENT_BUS.register(new com.nouho.soulgravestone.events.RespawnEvents());
        // Register our mod's ModConfigSpec so that FML can create and load the config file for us
        modContainer.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
    
    //Records the gravestone position for a player
    public static void recordGravestonePosition(Player player, BlockPos pos, Level level) {
        lastGravestonePositions.put(player.getUUID(), pos);
        lastGravestoneLevels.put(player.getUUID(), level);
    }
    
    //Gets the last gravestone position for a player
    public static BlockPos getLastGravestonePos(Player player) {
        return lastGravestonePositions.get(player.getUUID());
    }
    
    //Gets the last gravestone level/dimension for a player
    public static Level getLastGravestoneLevel(Player player) {
        return lastGravestoneLevels.get(player.getUUID());
    }
    
    //Clears the gravestone position for a player (called when gravestone is retrieved)
    public static void clearGravestonePosition(Player player) {
        lastGravestonePositions.remove(player.getUUID());
        lastGravestoneLevels.remove(player.getUUID());
    }
}
