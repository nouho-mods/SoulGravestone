package com.nouho.soulgravestone.soulshape;

import javax.annotation.Nonnull;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import com.nouho.soulgravestone.Config;


public class SoulShapeEffect extends MobEffect {
    // Track previous positions for movement detection
    private static final java.util.Map<java.util.UUID, Double> previousX = new java.util.HashMap<>();
    private static final java.util.Map<java.util.UUID, Double> previousZ = new java.util.HashMap<>();
    public SoulShapeEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x88E1FF); // Light blue color
    }      
      @Override
    public boolean applyEffectTick(@Nonnull LivingEntity entity, int amplifier) {
        // Note: Invisibility is now handled in RespawnEvents to prevent flickering
        // Slow Falling (vanilla: -0.125D) - only if enabled in config
        if (Config.soulShapeSlowFalling && !entity.onGround() && entity.getDeltaMovement().y < -0.125) {
            entity.setDeltaMovement(entity.getDeltaMovement().x, -0.125, entity.getDeltaMovement().z);
            entity.fallDistance = 0.0F;
        }        
        
        // Server-side particles only
        if (!entity.level().isClientSide) {
            // Ambient soul particles around the entity
            if (entity.level().random.nextInt(10) == 0) {
                // Spawn particles around the entity
                double entityX = entity.getX();
                double entityY = entity.getY();
                double entityZ = entity.getZ();
                
                // Create particles in a circle around the entity
                double angle = entity.level().random.nextDouble() * 2 * Math.PI;
                double radius = 0.5 + entity.level().random.nextDouble() * 0.2; // 0.5 to 0.7 block radius
                
                double x = entityX + Math.cos(angle) * radius;
                double y = entityY + entity.level().random.nextDouble() * entity.getBbHeight();
                double z = entityZ + Math.sin(angle) * radius;
                // Create gentle floating motion outward and upward
                double motionX = (x - entityX) * 0.02; // Gentle push outward from center
                double motionY = 0.02 + entity.level().random.nextDouble() * 0.03; // Upward motion
                double motionZ = (z - entityZ) * 0.02; // Gentle push outward from center
                
                // Use server-side particle spawning for multiplayer visibility
                ((net.minecraft.server.level.ServerLevel) entity.level()).sendParticles(
                    ParticleTypes.SOUL, x, y, z, 1, motionX, motionY, motionZ, 0.01);
            }
            
            // Soul Speed-like trail particles when moving - only for players
            if (entity instanceof Player player) {
                java.util.UUID playerId = player.getUUID();
                double currentX = player.getX();
                double currentZ = player.getZ();
                
                // Get previous positions
                Double prevX = previousX.get(playerId);
                Double prevZ = previousZ.get(playerId);                  
                if (prevX != null && prevZ != null && player.onGround()) {                    
                    // Calculate movement distance
                    double deltaX = currentX - prevX;
                    double deltaZ = currentZ - prevZ;                    double movementDistance = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
                    // Speed-dependent particle spawning - probability scales with movement speed (only when on ground)
                    if (movementDistance > 0.2) { // Minimum movement threshold                        // Calculate spawn chance based on speed (walking ~30%, sprinting ~56%)
                        // Use a curve that reduces walking chance but keeps sprinting similar
                        double speedFactor;
                        if (movementDistance < 0.25) {
                            // Walking range: reduce multiplier for lower speeds
                            speedFactor = Math.min(movementDistance * 1.2, 1.0);
                        } else {
                            // Sprinting range: use higher multiplier to maintain original levels
                            speedFactor = Math.min(0.3 + (movementDistance - 0.25) * 3.0, 1.0);
                        }
                        
                        if (player.level().random.nextDouble() < speedFactor) {
                            // Spawn particles directly under the player's feet
                            double trailX = currentX;
                            double trailZ = currentZ;
                            double trailY = player.getY() + 0.3; // Slightly above ground level
                            
                            // Add slight randomness to particle position
                            trailX += (player.level().random.nextDouble() - 0.5) * 0.3;
                            trailZ += (player.level().random.nextDouble() - 0.5) * 0.3;
                            
                            // Upward particle motion
                            double particleMotionY = 0.03 + player.level().random.nextDouble() * 0.04;
                            ((net.minecraft.server.level.ServerLevel) player.level()).sendParticles(
                                ParticleTypes.SOUL, trailX, trailY, trailZ, 1, 0.05, particleMotionY, 0.05, 0.01);
                        }
                    }
                }                
                // Update previous positions for next tick
                previousX.put(playerId, currentX);
                previousZ.put(playerId, currentZ);
            }
        }        
        return true;    }
    
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}