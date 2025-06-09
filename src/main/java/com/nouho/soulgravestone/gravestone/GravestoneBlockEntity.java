package com.nouho.soulgravestone.gravestone;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;


public class GravestoneBlockEntity extends BlockEntity {
    private NonNullList<ItemStack> inventory = NonNullList.withSize(41, ItemStack.EMPTY); // Start with base 41 slots: 0-35 main, 36-39 armor, 40 offhand
    private int storedXp = 0;
    private String playerName = "";
    private int soundTicker = 0; // Counter for sound timing
    private int nextSoundInterval = 0; // Next random interval for sound timing

    // Constructor for the gravestone block entity
    public GravestoneBlockEntity(BlockPos pos, BlockState state) {
        super(com.nouho.soulgravestone.SoulGravestone.GRAVESTONE_BLOCK_ENTITY.get(), pos, state);
    }

    // Dynamically resizes the inventory if needed.
    public void ensureInventorySize(Player player) {
        int requiredSize = calculateRequiredInventorySize(player);
        if (inventory.size() < requiredSize) {
            int oldSize = inventory.size();
            NonNullList<ItemStack> newInventory = NonNullList.withSize(requiredSize, ItemStack.EMPTY);
            for (int i = 0; i < inventory.size(); i++) {
                newInventory.set(i, inventory.get(i));
            }
            this.inventory = newInventory;
            com.nouho.soulgravestone.SoulGravestone.LOGGER.info("Gravestone inventory resized from " + oldSize +
                " to " + requiredSize + " slots for player " + player.getName().getString());
        }
    }

    // Returns the inventory stored in the gravestone
    public NonNullList<ItemStack> getInventory() {
        return inventory;
    }

    // Returns the amount of XP stored in the gravestone
    public int getStoredXp() {
        return storedXp;
    }

    // Sets the amount of XP stored in the gravestone
    public void setStoredXp(int xp) {
        this.storedXp = xp;
    }

    // Returns the player name associated with this gravestone
    public String getPlayerName() {
        return playerName;
    }

    // Sets the player name for this gravestone
    public void setPlayerName(String name) {
        this.playerName = name;
    }

    // Handles periodic sound effects for the gravestone
    public static void tick(net.minecraft.world.level.Level level, BlockPos pos, BlockState state, GravestoneBlockEntity blockEntity) {
        if (level.isClientSide) {
            blockEntity.soundTicker++;
            // Initialize the first interval if not set
            if (blockEntity.nextSoundInterval == 0) {
                blockEntity.nextSoundInterval = 80 + level.random.nextInt(61); // 80-140 ticks (4-7 seconds)
            }
            if (blockEntity.soundTicker >= blockEntity.nextSoundInterval) {
                // Randomize volume and pitch for more variation
                float volume = 0.7f + level.random.nextFloat() * 0.3f;
                float pitch = 0.6f + level.random.nextFloat() * 0.4f;
                level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    net.minecraft.sounds.SoundEvents.SOUL_ESCAPE.value(), net.minecraft.sounds.SoundSource.BLOCKS,
                    volume, pitch, false);
                blockEntity.soundTicker = 0; // Reset the timer
                blockEntity.nextSoundInterval = 80 + level.random.nextInt(61); // Set next random interval (4-7 seconds)
            }
        }
    }

    // Loads inventory and XP from NBT data
    @Override
    protected void loadAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        inventory.clear();
        ContainerHelper.loadAllItems(tag, inventory, provider);
        if (tag.contains("StoredXp")) {
            storedXp = tag.getInt("StoredXp");
        } else {
            storedXp = 0;
        }
        if (tag.contains("PlayerName")) {
            playerName = tag.getString("PlayerName");
        } else {
            playerName = "";
        }
    }

    // Saves inventory and XP to NBT data
    @Override
    protected void saveAdditional(@Nonnull CompoundTag tag, @Nonnull HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, inventory, provider);
        tag.putInt("StoredXp", storedXp);
        tag.putString("PlayerName", playerName);
    }

    // Calculates the required inventory size for a player including their Curios.
    public static int calculateRequiredInventorySize(Player player) {
        int baseSlots = 41; // 36 main + 4 armor + 1 offhand
        int curiosSlots = 0;
        if (net.neoforged.fml.ModList.get().isLoaded("curios")) {
            try {
                curiosSlots = com.nouho.soulgravestone.managers.CuriosManager.getTotalCuriosSlots(player);
            } catch (Exception e) {
                curiosSlots = 0;
            }
        }
        return baseSlots + curiosSlots;
    }

    // Validates that the gravestone has enough space for the player's items.
    public boolean hasEnoughSpaceFor(Player player) {
        int requiredSize = calculateRequiredInventorySize(player);
        return inventory.size() >= requiredSize;
    }
}
