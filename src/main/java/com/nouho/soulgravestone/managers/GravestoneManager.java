package com.nouho.soulgravestone.managers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

import com.nouho.soulgravestone.Config;
import com.nouho.soulgravestone.SoulGravestone;
import com.nouho.soulgravestone.gravestone.GravestoneBlock;
import com.nouho.soulgravestone.gravestone.GravestoneBlockEntity;


//Manages all gravestone-related functionality including placement, positioning, visual elements, and cleanup.
public class GravestoneManager {
    // Track last safe ground position for each player
    private static final Map<UUID, BlockPos> lastSafePositions = new HashMap<>();

    // Updates the last safe position for a player.
    public static void updatePlayerSafePosition(Player player) {
        if (player.level().isClientSide()) {
            return;
        }

        BlockPos pos = player.blockPosition();
        ServerLevel level = (ServerLevel) player.level();
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());

        boolean onSolid = below.isSolidRender(level, pos.below());
        boolean notLiquid = state.getFluidState().isEmpty();
        boolean notInVoid = !(level.dimension() == Level.END && pos.getY() < 0);

        if (onSolid && notLiquid && notInVoid) {
            lastSafePositions.put(player.getUUID(), pos);
        }
    }

    // Determines if gravestone creation should be allowed based on death conditions and config.
    public static boolean shouldCreateGravestone(ServerLevel level, BlockPos deathPos) {
        BlockState state = level.getBlockState(deathPos);
        boolean isLava = state.getFluidState().is(Fluids.LAVA);
        boolean isVoid = (level.dimension() == Level.END && deathPos.getY() < 0);

        if (isVoid) {
            return Config.gravestoneOnVoidDeath;
        } else if (isLava) {
            return Config.gravestoneOnLavaDeath;
        }

        return true; // Default: allow gravestone creation
    }

    // Determines the best position for gravestone placement based on death location and config.
    public static BlockPos findGravestonePosition(Player player, ServerLevel level, BlockPos deathPos) {
        BlockPos gravestonePos = deathPos;
        BlockState state = level.getBlockState(deathPos);

        boolean isWater = state.getFluidState().is(Fluids.WATER);
        boolean isLava = state.getFluidState().is(Fluids.LAVA);
        boolean isVoid = (level.dimension() == Level.END && deathPos.getY() < 0);

        // Void logic
        if (isVoid) {
            if (Config.gravestoneOnVoidDeath) {
                BlockPos lastSafe = lastSafePositions.get(player.getUUID());
                if (lastSafe != null && isSafeGravestonePos(level, lastSafe)) {
                    gravestonePos = lastSafe;
                }
            }
        }
        // Lava logic
        else if (isLava) {
            if (Config.gravestoneOnLavaDeath) {
                if (!Config.allowGravestoneInLava) {
                    BlockPos lastSafe = lastSafePositions.get(player.getUUID());
                    if (lastSafe != null && isSafeGravestonePos(level, lastSafe)) {
                        gravestonePos = lastSafe;
                    }
                } else {
                    BlockPos bottom = findBottomOfLiquid(level, deathPos, Fluids.LAVA);
                    if (isSafeGravestonePos(level, bottom)) {
                        gravestonePos = bottom;
                    } else {
                        BlockPos safe = lastSafePositions.get(player.getUUID());
                        if (safe != null && isSafeGravestonePos(level, safe)) {
                            gravestonePos = safe;
                        }
                    }
                }
            }
        }
        // Water logic
        else if (isWater) {
            if (!Config.allowGravestoneInWater) {
                BlockPos lastSafe = lastSafePositions.get(player.getUUID());
                if (lastSafe != null && isSafeGravestonePos(level, lastSafe)) {
                    gravestonePos = lastSafe;
                }
            } else {
                BlockPos bottom = findBottomOfLiquid(level, deathPos, Fluids.WATER);
                if (isSafeGravestonePos(level, bottom)) {
                    gravestonePos = bottom;
                } else {
                    BlockPos safe = lastSafePositions.get(player.getUUID());
                    if (safe != null && isSafeGravestonePos(level, safe)) {
                        gravestonePos = safe;
                    }
                }
            }
        }
        // Normal logic: if current pos is unsafe, use last safe position if available
        else if (!isSafeGravestonePos(level, deathPos)) {
            BlockPos lastSafe = lastSafePositions.get(player.getUUID());
            if (lastSafe != null && isSafeGravestonePos(level, lastSafe)) {
                gravestonePos = lastSafe;
            }
        }

        return gravestonePos;
    }

    // Creates and places a gravestone at the specified position.
    public static boolean createGravestone(Player player, ServerLevel level, BlockPos pos) {
        // Place the gravestone block facing the player's direction
        Direction playerFacing = player.getDirection();
        BlockState gravestoneState = SoulGravestone.GRAVESTONE_BLOCK.get().defaultBlockState()
            .setValue(GravestoneBlock.FACING, playerFacing);
        level.setBlockAndUpdate(pos, gravestoneState);

        // Check if the gravestone was actually placed
        BlockState placedState = level.getBlockState(pos);
        if (placedState.getBlock() instanceof GravestoneBlock) {
            // Record the gravestone position for lodestone compass functionality
            SoulGravestone.recordGravestonePosition(player, pos, level);

            // Set up gravestone with player info and XP
            var be = level.getBlockEntity(pos);
            if (be instanceof GravestoneBlockEntity gravestoneBE) {
                gravestoneBE.setPlayerName(player.getName().getString());

                // Store XP
                int xp = InventoryManager.getPlayerTotalXP(player);
                int retainedXp = (int) (xp * Config.xpRetainRatio);
                gravestoneBE.setStoredXp(retainedXp);

                // Create text display above the gravestone
                createGravestoneNameDisplay(level, pos, player.getName().getString());
            }

            return true;
        }

        return false;
    }

    // Handles gravestone retrieval when a player right-clicks a gravestone.
    public static void handleGravestoneRetrieval(PlayerInteractEvent.RightClickBlock event) {
        var level = event.getLevel();
        var pos = event.getPos();
        var player = event.getEntity();
        var state = level.getBlockState(pos);
        Block block = state.getBlock();

        if (block instanceof GravestoneBlock) {
            if (!level.isClientSide && event.getHand() == InteractionHand.MAIN_HAND && !player.isCrouching()) {
                // Only allow retrieval if player has empty hands
                ItemStack heldItem = player.getItemInHand(event.getHand());
                if (!heldItem.isEmpty()) {
                    return;
                }

                // Check if player has Soul Shape effect before retrieval
                boolean hadSoulShape = player.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT);

                // Remove lodestone compass from player's inventory before retrieving items
                var inventory = player.getInventory();
                for (int i = 0; i < inventory.getContainerSize(); i++) {
                    ItemStack stack = inventory.getItem(i);
                    if (stack.getItem() == Items.COMPASS && stack.has(DataComponents.LODESTONE_TRACKER)) {
                        inventory.removeItem(i, stack.getCount());
                        SoulGravestone.clearGravestonePosition(player);
                        break;
                    }
                }

                // Retrieve the gravestone
                block.playerWillDestroy(level, pos, state, player);
                level.removeBlock(pos, false);

                // Play retrieval sound effect
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_LEATHER,
                    SoundSource.PLAYERS, 1.0f,
                    1.0f + (level.random.nextFloat() - level.random.nextFloat()) * 0.4f);

                // Handle Soul Shape effect
                if (hadSoulShape) {
                    player.removeEffect(SoulGravestone.SOUL_SHAPE_EFFECT);
                    player.addEffect(new MobEffectInstance(
                        SoulGravestone.SOUL_SHAPE_EFFECT,
                        Config.soulShapeRetrieveDurationTicks,
                        0, false, false, true));
                }

                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
        }
    }

    // Removes ArmorStand name displays near the gravestone during cleanup.
    public static void removeGravestoneNameDisplay(Level level, BlockPos pos) {
        if (!level.isClientSide) {
            try {
                // Search for ArmorStands
                Vec3 center = Vec3.atCenterOf(pos);
                AABB searchArea = new AABB(center.add(-0.6, -4.0, -0.6), center.add(0.6, 1.0, 0.6));

                var armorStands = level.getEntitiesOfClass(ArmorStand.class, searchArea,
                    armorStand -> {
                        // Must be invisible
                        if (!armorStand.isInvisible()) {
                            return false;
                        }
                        // Must have custom name visible and a custom name
                        if (!armorStand.hasCustomName() || !armorStand.isCustomNameVisible()) {
                            return false;
                        }
                        // Must have no gravity (as set in our creation method)
                        if (!armorStand.isNoGravity()) {
                            return false;
                        }
                        // Must be invulnerable (as set in our creation method)
                        if (!armorStand.isInvulnerable()) {
                            return false;
                        }
                        // Custom name must end with "'s grave"
                        var customName = armorStand.getCustomName();
                        return customName != null && customName.getString().endsWith("'s grave");
                    });

                // Remove all matching ArmorStands
                for (ArmorStand armorStand : armorStands) {
                    armorStand.discard();
                }
            } catch (Exception e) {
                // Silently continue if cleanup fails - gravestone removal shouldn't be blocked
            }
        }
    }

    // Helper: Checks if a position is safe for gravestone placement.
    public static boolean isSafeGravestonePos(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        BlockState below = level.getBlockState(pos.below());
        boolean replaceable = state.isAir() || state.canBeReplaced();
        boolean notLiquid = state.getFluidState().isEmpty();
        boolean solidBelow = below.isSolidRender(level, pos.below());
        return replaceable && notLiquid && solidBelow;
    }

    // Helper: Finds the bottom of a liquid column (lava or water) starting from a given position.
    private static BlockPos findBottomOfLiquid(ServerLevel level, BlockPos start, Fluid fluidType) {
        BlockPos pos = start;
        // Go down as long as the block is the same fluid (source or flowing)
        while (pos.getY() > level.getMinBuildHeight()) {
            BlockState state = level.getBlockState(pos);
            if (!state.getFluidState().is(fluidType)) {
                break;
            }
            BlockPos below = pos.below();
            BlockState belowState = level.getBlockState(below);
            if (!belowState.getFluidState().is(fluidType)) {
                // Found the bottom
                return pos;
            }
            pos = below;
        }
        return start; // fallback if not found
    }

    // Helper: Creates a text display above the gravestone using an invisible armor stand with custom name.
    private static void createGravestoneNameDisplay(ServerLevel level, BlockPos gravestonePos, String playerName) {
        try {
            // Create an invisible armor stand to display the player name
            ArmorStand nameDisplay = new ArmorStand(EntityType.ARMOR_STAND, level);

            // Position the armor stand directly at the block position with small offset
            double x = gravestonePos.getX() + 0.5;
            double y = gravestonePos.getY() - 1.0;
            double z = gravestonePos.getZ() + 0.5;
            nameDisplay.setPos(x, y, z);

            // Configure the armor stand properties
            nameDisplay.setInvisible(true); // Make the armor stand invisible
            nameDisplay.setNoGravity(true); // Prevent it from falling
            nameDisplay.setInvulnerable(true); // Make it indestructible
            nameDisplay.setNoBasePlate(true); // Remove the base plate
            nameDisplay.setShowArms(false); // Hide arms
            nameDisplay.setSilent(true); // Make it silent
            nameDisplay.setCustomName(Component.literal(playerName + "'s grave")); // Set the display name
            nameDisplay.setCustomNameVisible(true); // Make the name always visible
            // Add the armor stand to the world
            level.addFreshEntity(nameDisplay);
        } catch (Exception e) {
            // If name display creation fails, silently continue without it
        }
    }
}
