package com.nouho.soulgravestone.managers;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;

import com.nouho.soulgravestone.gravestone.GravestoneBlockEntity;


//Manages Curios integration for the Soul Gravestone mod.
public class CuriosManager {    //Captures the pre-death state of all Curios items for a player.
    public static List<ItemStack> capturePreDeathCurios(Player player) {
        List<ItemStack> preDeathCurios = new ArrayList<>();
        
        try {
            // Use direct Curios API call instead of reflection
            var curiosInventoryOpt = CuriosApi.getCuriosInventory(player);
            
            if (curiosInventoryOpt.isPresent()) {
                var curiosInventory = curiosInventoryOpt.get();
                
                // Get all curios items using API methods
                var curios = curiosInventory.getCurios();
                
                // FIRST PASS: Handle ALL regular Curios items
                for (var entry : curios.entrySet()) {
                    var curiosStacksHandler = entry.getValue();
                    
                    // Handle regular Curios items
                    var stackHandler = curiosStacksHandler.getStacks();
                    int size = stackHandler.getSlots();
                      // Copy all regular items from this curios type (including empty slots to maintain position)
                    for (int i = 0; i < size; i++) {
                        ItemStack stack = stackHandler.getStackInSlot(i);
                        preDeathCurios.add(stack.copy()); // Always add, even if empty, to maintain slot positions
                    }
                }
                
                // SECOND PASS: Handle ALL cosmetic Curios items
                for (var entry : curios.entrySet()) {
                    var curiosStacksHandler = entry.getValue();
                    
                    // Handle cosmetic Curios items
                    var cosmeticStackHandler = curiosStacksHandler.getCosmeticStacks();
                    int cosmeticSize = cosmeticStackHandler.getSlots();
                    
                    // Copy all cosmetic items from this curios type (including empty slots to maintain position)
                    for (int i = 0; i < cosmeticSize; i++) {
                        ItemStack stack = cosmeticStackHandler.getStackInSlot(i);
                        preDeathCurios.add(stack.copy()); // Always add, even if empty, to maintain slot positions
                    }
                }
            }
        } catch (Exception e) {
            // Error capturing pre-death Curios items - silent failure
        }
        
        return preDeathCurios;
    }

    //Handles Curios items during PlayerEvent.Clone by comparing pre-death and post-clone states.
    public static void handlePlayerCloneCuriosWithComparison(Player oldPlayer, Player newPlayer, 
            GravestoneBlockEntity gravestoneBE, List<ItemStack> preDeathCuriosItems) {
        
        if (preDeathCuriosItems == null || preDeathCuriosItems.isEmpty()) {
            return;
        }
        // Schedule the comparison to happen after Curios inventory is properly initialized
        // This fixes the timing issue where newPlayer's Curios inventory is empty during clone event
        var server = newPlayer.level().getServer();
        if (server != null) {
            server.tell(new net.minecraft.server.TickTask(1, () -> {
                try {
                    // Get current Curios state from new player (after 1 tick delay)
                    List<ItemStack> currentCurios = getCurrentCuriosItems(newPlayer);
                    
                    // Compare pre-death vs current state and transfer lost items
                    transferLostCuriosToGravestone(preDeathCuriosItems, currentCurios, gravestoneBE);
                    
                } catch (Exception e) {
                    // Error in delayed Curios handling - silent failure
                }
            }));
        } else {
            // Fallback to immediate comparison if server is not available
            try {
                List<ItemStack> currentCurios = getCurrentCuriosItems(newPlayer);
                transferLostCuriosToGravestone(preDeathCuriosItems, currentCurios, gravestoneBE);
            } catch (Exception e) {
                // Error in fallback Curios handling - silent failure
            }
        }
    }

    //Gets the total number of Curios slots for a player.
    public static int getTotalCuriosSlots(Player player) {
        int totalSlots = 0;
        
        try {
            // Use direct Curios API call instead of reflection
            var curiosInventoryOpt = CuriosApi.getCuriosInventory(player);
            
            if (curiosInventoryOpt.isPresent()) {
                var curiosInventory = curiosInventoryOpt.get();
                
                // Get all curios items using API methods
                var curios = curiosInventory.getCurios();
                
                for (var entry : curios.entrySet()) {
                    var curiosStacksHandler = entry.getValue();
                    
                    // Count regular Curios slots
                    var stackHandler = curiosStacksHandler.getStacks();
                    int size = stackHandler.getSlots();
                    totalSlots += size;
                    
                    // Count cosmetic Curios slots
                    var cosmeticStackHandler = curiosStacksHandler.getCosmeticStacks();
                    int cosmeticSize = cosmeticStackHandler.getSlots();
                    totalSlots += cosmeticSize;
                }
            }
        } catch (Exception e) {
            // Error getting total Curios slots - silent failure
            return 0; // Return 0 on error to avoid issues
        }
        
        return totalSlots;
    }    //Handles retrieving Curios items from the gravestone when a player opens it.
    public static void handlePlayerRetrieveCurios(Player player, GravestoneBlockEntity gravestoneBE) {        try {
            var curiosInventoryOpt = CuriosApi.getCuriosInventory(player);
            if (!curiosInventoryOpt.isPresent()) return;
            
            var curiosInventory = curiosInventoryOpt.get();
            var curios = curiosInventory.getCurios();
              // Build slot mapping: global index -> actual curios slot (same order as capture/storage)
            List<SlotMapping> slotMappings = new ArrayList<>();
            
            // Build mapping in EXACT SAME ORDER as capture/storage - ALL regular slots first
            for (var entry : curios.entrySet()) {
                var curiosStacksHandler = entry.getValue();
                var stackHandler = curiosStacksHandler.getStacks();
                
                for (int slotIndex = 0; slotIndex < stackHandler.getSlots(); slotIndex++) {
                    slotMappings.add(new SlotMapping(slotIndex, false, curiosStacksHandler));
                }
            }
            
            // Then ALL cosmetic slots
            for (var entry : curios.entrySet()) {
                var curiosStacksHandler = entry.getValue();
                var cosmeticStackHandler = curiosStacksHandler.getCosmeticStacks();
                
                for (int slotIndex = 0; slotIndex < cosmeticStackHandler.getSlots(); slotIndex++) {
                    slotMappings.add(new SlotMapping(slotIndex, true, curiosStacksHandler));
                }
            }
            
            // Now retrieve items using the correct mapping
            for (int gravestoneSlot = 41; gravestoneSlot < gravestoneBE.getInventory().size(); gravestoneSlot++) {
                ItemStack storedStack = gravestoneBE.getInventory().get(gravestoneSlot);
                
                if (!storedStack.isEmpty()) {
                    int targetGlobalIndex = gravestoneSlot - 41; // Convert gravestone slot back to global index
                    
                    if (targetGlobalIndex < slotMappings.size()) {
                        SlotMapping mapping = slotMappings.get(targetGlobalIndex);
                        
                        // Try to place in the correct slot
                        if (mapping.getStackInSlot().isEmpty()) {
                            mapping.setStackInSlot(storedStack.copy());
                            gravestoneBE.getInventory().set(gravestoneSlot, ItemStack.EMPTY);
                        } else {
                            // If original slot is occupied, fallback to inventory/ground
                            if (!player.getInventory().add(storedStack.copy())) {
                                player.drop(storedStack.copy(), false);
                            }
                            gravestoneBE.getInventory().set(gravestoneSlot, ItemStack.EMPTY);
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            // Error retrieving Curios items - silent failure
        }
    }//Helper: Gets the current Curios items from a player (post-respawn state).
    private static List<ItemStack> getCurrentCuriosItems(Player player) {
        List<ItemStack> currentCurios = new ArrayList<>();
        
        try {
            // Use direct Curios API call instead of reflection
            var curiosInventoryOpt = CuriosApi.getCuriosInventory(player);
            
            if (curiosInventoryOpt.isPresent()) {
                var curiosInventory = curiosInventoryOpt.get();
                
                // Get all curios items using API methods
                var curios = curiosInventory.getCurios();
                
                // FIRST PASS: Handle ALL regular Curios items
                for (var entry : curios.entrySet()) {
                    var curiosStacksHandler = entry.getValue();
                    
                    // Handle regular Curios items
                    var stackHandler = curiosStacksHandler.getStacks();
                    int size = stackHandler.getSlots();
                      // Copy all regular items from this curios type (including empty slots to maintain position)
                    for (int i = 0; i < size; i++) {
                        ItemStack stack = stackHandler.getStackInSlot(i);
                        currentCurios.add(stack.copy()); // Always add, even if empty, to maintain slot positions
                    }
                }
                
                // SECOND PASS: Handle ALL cosmetic Curios items
                for (var entry : curios.entrySet()) {
                    var curiosStacksHandler = entry.getValue();
                    
                    // Handle cosmetic Curios items
                    var cosmeticStackHandler = curiosStacksHandler.getCosmeticStacks();
                    int cosmeticSize = cosmeticStackHandler.getSlots();
                    
                    // Copy all cosmetic items from this curios type (including empty slots to maintain position)
                    for (int i = 0; i < cosmeticSize; i++) {
                        ItemStack stack = cosmeticStackHandler.getStackInSlot(i);
                        currentCurios.add(stack.copy()); // Always add, even if empty, to maintain slot positions
                    }
                }
            }
        } catch (Exception e) {
            // Error getting current Curios items - silent failure
        }
        
        return currentCurios;
    }//Helper: Compares pre-death and current Curios inventories to transfer only lost items.
    private static void transferLostCuriosToGravestone(List<ItemStack> preDeathCurios, 
            List<ItemStack> currentCurios, GravestoneBlockEntity gravestoneBE) {
        
        // Compare each pre-death Curios item by slot position (like armor)
        for (int slotIndex = 0; slotIndex < preDeathCurios.size(); slotIndex++) {
            ItemStack preDeathStack = preDeathCurios.get(slotIndex);
              // Skip empty stacks
            if (preDeathStack.isEmpty()) {
                continue;
            }
            
            // Get corresponding current stack (if exists)
            ItemStack currentStack = (slotIndex < currentCurios.size()) ? 
                currentCurios.get(slotIndex) : ItemStack.EMPTY;
                  // If item was lost (using same logic as normal inventory)
            if (!ItemStack.matches(preDeathStack, currentStack)) {
                // Store using DIRECT POSITION MAPPING (like armor)
                int gravestoneSlot = 41 + slotIndex;  // Curios slot X → Gravestone slot (41 + X)
                
                // Check if we have space in gravestone
                if (gravestoneSlot < gravestoneBE.getInventory().size()) {
                    // Store the lost item in its exact position (like armor)
                    gravestoneBE.getInventory().set(gravestoneSlot, preDeathStack.copy());
                } else {
                    // Gravestone is full - silent failure
                    break;
                }            }
        }
    }    //Helper class for slot mapping
    private static class SlotMapping {
        final int slotIndex;
        final boolean isCosmetic;
        final top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler curiosHandler;
        
        SlotMapping(int slotIndex, boolean isCosmetic, top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler curiosHandler) {
            this.slotIndex = slotIndex;
            this.isCosmetic = isCosmetic;
            this.curiosHandler = curiosHandler;
        }
          ItemStack getStackInSlot() {
            try {
                if (isCosmetic) {
                    return curiosHandler.getCosmeticStacks().getStackInSlot(slotIndex);
                } else {
                    return curiosHandler.getStacks().getStackInSlot(slotIndex);
                }
            } catch (Exception e) {
                return ItemStack.EMPTY;
            }
        }
        
        void setStackInSlot(ItemStack stack) {
            try {
                if (isCosmetic) {
                    curiosHandler.getCosmeticStacks().setStackInSlot(slotIndex, stack);
                } else {
                    curiosHandler.getStacks().setStackInSlot(slotIndex, stack);
                }
            } catch (Exception e) {
                // Failed to set stack - silent failure
            }
        }
    }
}
