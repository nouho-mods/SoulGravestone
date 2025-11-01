package com.nouho.soulgravestone.managers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.neoforged.fml.ModList;

import com.nouho.soulgravestone.SoulGravestone;
import com.nouho.soulgravestone.gravestone.GravestoneBlockEntity;


//Manages inventory capture, comparison, transfer, restoration, and XP calculations for gravestones.
public class InventoryManager {
    // Track pre-death inventory state for PlayerEvent.Clone
    private static final Map<UUID, List<ItemStack>> preDeathInventories = new HashMap<>();
    private static final Map<UUID, List<ItemStack>> preDeathCurios = new HashMap<>();

    //Captures the pre-death state of a player's inventory.
    public static void capturePreDeathInventory(Player player) {
        List<ItemStack> preDeathInventory = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            preDeathInventory.add(player.getInventory().getItem(i).copy());
        }
        for (int i = 0; i < 4; i++) {
            preDeathInventory.add(player.getInventory().armor.get(i).copy());
        }
        preDeathInventory.add(player.getInventory().offhand.get(0).copy());
        preDeathInventories.put(player.getUUID(), preDeathInventory);
        if (ModList.get().isLoaded("curios")) {
            List<ItemStack> preDeathCuriosItems = CuriosManager.capturePreDeathCurios(player);
            preDeathCurios.put(player.getUUID(), preDeathCuriosItems);
        }
    }

    //Gets the pre-death inventory for a player.
    public static List<ItemStack> getPreDeathInventory(UUID playerId) {
        return preDeathInventories.get(playerId);
    }

    //Gets the pre-death Curios items for a player.
    public static List<ItemStack> getPreDeathCurios(UUID playerId) {
        return preDeathCurios.get(playerId);
    }

    //Transfers lost items from pre-death inventory to gravestone by comparing inventories.
    public static void transferLostItemsToGravestone(Player newPlayer, List<ItemStack> preDeathInventory, GravestoneBlockEntity gravestoneBE) {
        // Compare main inventory (0-35)
        for (int i = 0; i < 36; i++) {
            ItemStack preDeathStack = (i < preDeathInventory.size()) ? preDeathInventory.get(i) : ItemStack.EMPTY;
            ItemStack currentStack = newPlayer.getInventory().getItem(i);            // If item was lost (not in current inventory or quantity reduced)
            if (!preDeathStack.isEmpty() &&
                !ItemStack.matches(preDeathStack, currentStack)) {
                // Store the lost item in gravestone
                gravestoneBE.getInventory().set(i, preDeathStack.copy());
            }
        }
        // Compare armor (36-39)
        for (int i = 0; i < 4; i++) {
            int preDeathIndex = 36 + i;
            ItemStack preDeathStack = (preDeathIndex < preDeathInventory.size()) ? preDeathInventory.get(preDeathIndex) : ItemStack.EMPTY;
            ItemStack currentStack = newPlayer.getInventory().armor.get(i);
            // If armor was lost
            if (!preDeathStack.isEmpty() && !ItemStack.matches(preDeathStack, currentStack)) {
                gravestoneBE.getInventory().set(36 + i, preDeathStack.copy());
            }
        }
        // Compare offhand (40)
        ItemStack preDeathOffhand = (40 < preDeathInventory.size()) ? preDeathInventory.get(40) : ItemStack.EMPTY;
        ItemStack currentOffhand = newPlayer.getInventory().offhand.get(0);        if (!preDeathOffhand.isEmpty() &&
            !ItemStack.matches(preDeathOffhand, currentOffhand)) {
            gravestoneBE.getInventory().set(40, preDeathOffhand.copy());
        }
        // CRITICAL: Mark BlockEntity as changed so Minecraft saves it to disk!
        gravestoneBE.setChanged();
    }

    // Restores all gravestone contents (items, XP, Curios) to the specified player.
    public static void restoreGravestoneContents(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return;
        // Remove lodestone compass from player's inventory
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getItem() == Items.COMPASS && stack.has(net.minecraft.core.component.DataComponents.LODESTONE_TRACKER)) {
                inventory.removeItem(i, stack.getCount());
                SoulGravestone.clearGravestonePosition(player);
                break;
            }
        }
        var be = level.getBlockEntity(pos);
        if (be instanceof GravestoneBlockEntity gravestoneBE) {
            var inv = gravestoneBE.getInventory();
            var playerInv = player.getInventory();
            // Main inventory (0-35)
            for (int i = 0; i < 36; i++) {
                ItemStack stack = inv.get(i);
                if (!stack.isEmpty()) {
                    if (playerInv.getItem(i).isEmpty()) {
                        playerInv.setItem(i, stack);
                    } else if (!playerInv.add(stack)) {
                        player.drop(stack, false);
                    }
                    inv.set(i, ItemStack.EMPTY);
                }
            }
            // Armor (36-39)
            for (int i = 0; i < 4; i++) {
                ItemStack stack = inv.get(36 + i);
                if (!stack.isEmpty()) {
                    if (playerInv.armor.get(i).isEmpty()) {
                        playerInv.armor.set(i, stack);
                    } else if (!playerInv.add(stack)) {
                        player.drop(stack, false);
                    }
                    inv.set(36 + i, ItemStack.EMPTY);
                }
            }
            // Offhand (40)
            ItemStack offhandStack = inv.get(40);
            if (!offhandStack.isEmpty()) {
                if (playerInv.offhand.get(0).isEmpty()) {
                    playerInv.offhand.set(0, offhandStack);
                } else if (!playerInv.add(offhandStack)) {
                    player.drop(offhandStack, false);
                }
                inv.set(40, ItemStack.EMPTY);
            }
            // Restore XP
            int storedXp = gravestoneBE.getStoredXp();
            if (storedXp > 0) {
                player.giveExperiencePoints(storedXp);
                gravestoneBE.setStoredXp(0);
            }
            // Restore Curios items
            if (ModList.get().isLoaded("curios")) {
                CuriosManager.handlePlayerRetrieveCurios(player, gravestoneBE);
            }
        }
    }

    // Cleans up tracking data for a player.
    public static void cleanupPlayerData(UUID playerId) {
        preDeathInventories.remove(playerId);
        preDeathCurios.remove(playerId);
    }

    // Calculates the total XP from a player's level and progress.
    public static int getPlayerTotalXP(Player player) {
        int level = player.experienceLevel;
        float progress = player.experienceProgress;
        int xp = getTotalXpForLevel(level);
        xp += (int)(progress * player.getXpNeededForNextLevel());
        return xp;
    }

    // Returns the vanilla XP formula for total XP at a given level.
    private static int getTotalXpForLevel(int level) {
        if (level <= 16) {
            return level * level + 6 * level;
        } else if (level <= 31) {
            return (int)(2.5 * level * level - 40.5 * level + 360);
        } else {
            return (int)(4.5 * level * level - 162.5 * level + 2220);
        }
    }
}
