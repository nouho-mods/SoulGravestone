package com.nouho.soulgravestone.events;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import com.nouho.soulgravestone.SoulGravestone;


// Handles all events related to the Soul Shape effect and respawn mechanics
public class RespawnEvents {
    private static final ResourceLocation SOUL_SHAPE_SPEED_ID = ResourceLocation.fromNamespaceAndPath("soulgravestone", "soul_shape_speed");

    //Handles the player respawn event: applies Soul Shape effect and gives a recovery compass
    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        
        // Only apply Soul Shape effect and give compass if this is an actual death respawn,
        // not a dimension change (e.g., returning from the End)
        if (!event.isEndConquered()) {
            player.addEffect(new MobEffectInstance(
                SoulGravestone.SOUL_SHAPE_EFFECT,
                com.nouho.soulgravestone.Config.soulShapeDurationTicks,
                0, false, false, true));
            
            var gravestonePos = SoulGravestone.getLastGravestonePos(player);
            var gravestoneLevel = SoulGravestone.getLastGravestoneLevel(player);
            if (gravestonePos != null && gravestoneLevel != null) {
                ItemStack lodestoneCompass = new ItemStack(Items.COMPASS);
                GlobalPos globalPos = GlobalPos.of(gravestoneLevel.dimension(), gravestonePos);
                lodestoneCompass.set(DataComponents.LODESTONE_TRACKER, new LodestoneTracker(java.util.Optional.of(globalPos), false));
                if (!player.getInventory().add(lodestoneCompass)) {
                    player.drop(lodestoneCompass, false);
                }
            }
        }
    }    
    
    //Handles player tick: applies or removes movement speed bonus and invisibility for Soul Shape
    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        
        if (player.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT)) {
            // Apply invisibility if not already invisible
            if (!player.isInvisible()) {
                player.setInvisible(true);
            }
            
            // Apply speed bonus if configured
            if (com.nouho.soulgravestone.Config.soulShapeSpeedBonus > 0) {
                var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
                if (attr != null && attr.getModifier(SOUL_SHAPE_SPEED_ID) == null) {
                    attr.addTransientModifier(new AttributeModifier(
                        SOUL_SHAPE_SPEED_ID,
                        com.nouho.soulgravestone.Config.soulShapeSpeedBonus,
                        AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
                    ));
                }
            }
        } else {
            // Remove invisibility when effect is not present
            if (player.isInvisible()) {
                player.setInvisible(false);
            }
            
            // Remove speed bonus
            var attr = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (attr != null && attr.getModifier(SOUL_SHAPE_SPEED_ID) != null) {
                attr.removeModifier(SOUL_SHAPE_SPEED_ID);
            }
        }
    }

    //Cancels mob targeting if the player has the Soul Shape effect
    @SubscribeEvent
    public void onMobTarget(LivingChangeTargetEvent event) {
        LivingEntity newTarget = event.getNewAboutToBeSetTarget();
        if (newTarget instanceof Player player && player.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT)) {
            event.setCanceled(true);
        }
    }

    //Handles container opening: prevents ghosts from accessing loot chests
    @SubscribeEvent
    public void onContainerOpen(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!player.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT)) {
            return;
        }
        Block block = event.getLevel().getBlockState(event.getPos()).getBlock();
        if (block instanceof ChestBlock || block instanceof BarrelBlock || block instanceof ShulkerBoxBlock) {
            BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
            if (blockEntity instanceof RandomizableContainerBlockEntity randomizableContainer) {
                if (randomizableContainer.getLootTable() != null) {
                    event.setCanceled(true);
                    if (!player.level().isClientSide) {
                        player.displayClientMessage(
                            Component.literal("§7You cannot access loot chests while in Soul Shape"),
                            true
                        );
                    }
                }
            }        
        }
    }    
    
    //Handles left-click interactions: prevents block breaking animation from starting
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        Player player = event.getEntity();
        if (!player.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT)) {
            return;
        }
        if (com.nouho.soulgravestone.Config.soulShapePreventBlockBreaking) {
            event.setCanceled(true);
            if (!player.level().isClientSide) {
                player.displayClientMessage(
                    Component.literal("§7You cannot break blocks while in Soul Shape"),
                    true
                );
            }
        }
    }
    
    //Handles actual block breaking: removes Soul Shape effect when breaking blocks
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof Player player) {
            if (player.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT)) {
                if (com.nouho.soulgravestone.Config.soulShapeRemoveOnBlockBreak) {
                    player.removeEffect(SoulGravestone.SOUL_SHAPE_EFFECT);
                }
            }
        }
    }

    //Handles incoming damage: applies invincibility or removes Soul Shape on attack based on config
    @SubscribeEvent
    public void onLivingIncomingDamage(LivingIncomingDamageEvent event) {
        var entity = event.getEntity();
        var damageSource = event.getSource();
        boolean victimHasSoulShape = entity.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT);
        boolean attackerHasSoulShape = damageSource.getEntity() instanceof Player attacker &&
                                      attacker.hasEffect(SoulGravestone.SOUL_SHAPE_EFFECT);
        if (!victimHasSoulShape && !attackerHasSoulShape) {
            return;
        }
        if (victimHasSoulShape && com.nouho.soulgravestone.Config.soulShapeInvincible) {
            event.setCanceled(true);
            return;
        }
        if (attackerHasSoulShape && damageSource.getEntity() instanceof Player attacker) {
            if (com.nouho.soulgravestone.Config.soulShapePreventAttacks) {
                event.setCanceled(true);
            } else {            attacker.removeEffect(SoulGravestone.SOUL_SHAPE_EFFECT);
            }
        }    
    }
}
