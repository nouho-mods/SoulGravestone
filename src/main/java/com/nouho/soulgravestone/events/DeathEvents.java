package com.nouho.soulgravestone.events;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.nouho.soulgravestone.SoulGravestone;
import com.nouho.soulgravestone.gravestone.GravestoneBlockEntity;
import com.nouho.soulgravestone.managers.CuriosManager;
import com.nouho.soulgravestone.managers.GravestoneManager;
import com.nouho.soulgravestone.managers.InventoryManager;

// Handles all gravestone-related events including death, respawn, and retrieval.
public class DeathEvents {
    // Track gravestone info for PlayerEvent.Clone
    private static final Map<UUID, GravestoneInfo> gravestoneInfoMap = new HashMap<>();

    // Called every tick for each player. Updates the last safe position of the player.
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        GravestoneManager.updatePlayerSafePosition(player);
    }

    // Called when a player dies. Handles gravestone placement and stores pre-death inventory state.
    @SubscribeEvent
    public void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (player.level().isClientSide()) return;
        // Capture pre-death inventory state
        InventoryManager.capturePreDeathInventory(player);

        ServerLevel level = (ServerLevel) player.level();
        BlockPos deathPos = player.blockPosition();

        // Determine if we should create a gravestone
        boolean shouldCreate = GravestoneManager.shouldCreateGravestone(level, deathPos);

        if (shouldCreate) {
            BlockPos gravestonePos = GravestoneManager.findGravestonePosition(player, level, deathPos);
            boolean created = GravestoneManager.createGravestone(player, level, gravestonePos);
            gravestoneInfoMap.put(player.getUUID(), new GravestoneInfo(gravestonePos, level, created));
        } else {
            // Should not create gravestone
            gravestoneInfoMap.put(player.getUUID(), new GravestoneInfo(deathPos, level, false));
        }
    }

    // Called when player is cloned (after death/respawn). Handles inventory comparison and item transfer.
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        if (event.getEntity().level().isClientSide()) return;
        Player newPlayer = event.getEntity();
        Player oldPlayer = event.getOriginal();
        UUID playerId = newPlayer.getUUID();
        // Get gravestone info
        GravestoneInfo gravestoneInfo = gravestoneInfoMap.get(playerId);
        if (gravestoneInfo == null || !gravestoneInfo.created) {
            // Clean up tracking data
            InventoryManager.cleanupPlayerData(playerId);
            gravestoneInfoMap.remove(playerId);
            return;
        }
        // Get pre-death inventory
        List<ItemStack> preDeathInventory = InventoryManager.getPreDeathInventory(playerId);
        if (preDeathInventory == null) {
            SoulGravestone.LOGGER.warn("No pre-death inventory found for player: " + newPlayer.getName().getString());
            InventoryManager.cleanupPlayerData(playerId);
            gravestoneInfoMap.remove(playerId);
            return;
        }
        // Find gravestone to store items
        var be = gravestoneInfo.level.getBlockEntity(gravestoneInfo.position);
        if (!(be instanceof GravestoneBlockEntity gravestoneBE)) {
            SoulGravestone.LOGGER.warn("Could not find gravestone for player: " + newPlayer.getName().getString());
            InventoryManager.cleanupPlayerData(playerId);
            gravestoneInfoMap.remove(playerId);
            return;
        }
        // Ensure gravestone has enough slots for all player items (including Curios)
        gravestoneBE.ensureInventorySize(newPlayer);
        InventoryManager.transferLostItemsToGravestone(newPlayer, preDeathInventory, gravestoneBE);
        // Handle Curios items if Curios is loaded
        if (ModList.get().isLoaded("curios")) {
            List<ItemStack> preDeathCuriosItems = InventoryManager.getPreDeathCurios(playerId);
            CuriosManager.handlePlayerCloneCuriosWithComparison(oldPlayer, newPlayer, gravestoneBE, preDeathCuriosItems);
        }
        // Clean up tracking data
        InventoryManager.cleanupPlayerData(playerId);
        gravestoneInfoMap.remove(playerId);
    }

    // Called when a player dies to prevent XP orbs from dropping.
    @SubscribeEvent
    public void onLivingExperienceDrop(LivingExperienceDropEvent event) {
        if (event.getEntity() instanceof Player) {
            event.setDroppedExperience(0);
        }
    }

    // Called when entity drops items on death. Prevents players from dropping items on ground.
    @SubscribeEvent
    public void onLivingDrops(LivingDropsEvent event) {
        if (event.getEntity() instanceof Player player) {
            // Only prevent item drops if a gravestone was successfully created for this player
            GravestoneInfo gravestoneInfo = gravestoneInfoMap.get(player.getUUID());
            if (gravestoneInfo != null && gravestoneInfo.created) {
                // Cancel item drops for players - items will be handled by our dual-event system
                event.getDrops().clear();
                event.setCanceled(true);
            }
            // If no gravestone was created or creation failed, let items drop normally
        }
    }

    // Called when a player right-clicks a gravestone block. Handles gravestone retrieval.
    @SubscribeEvent
    public void onRightClickGravestone(PlayerInteractEvent.RightClickBlock event) {
        GravestoneManager.handleGravestoneRetrieval(event);
    }

    // Helper class to store gravestone information
    private static class GravestoneInfo {
        final BlockPos position;
        final ServerLevel level;
        final boolean created;

        GravestoneInfo(BlockPos position, ServerLevel level, boolean created) {
            this.position = position;
            this.level = level;
            this.created = created;
        }
    }
}
