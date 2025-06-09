package com.nouho.soulgravestone.gravestone;

import javax.annotation.Nonnull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;


// The main gravestone block class, defines shape, placement, and basic block behavior
public class GravestoneBlock extends Block implements EntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape GRAVESTONE_SHAPE = Shapes.or(
        Block.box(0, 0, 0, 16, 3, 16),    // Full-width base (3 blocks high)
        Block.box(2, 3, 2, 14, 14, 14),   // Middle section (11 blocks high)
        Block.box(3, 14, 3, 13, 16, 13)   // Top section (2 blocks high)
    );

    // Constructor: Initializes the gravestone block with given properties
    public GravestoneBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // Returns the collision shape for the block (player collision)
    @Override
    public VoxelShape getShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return GRAVESTONE_SHAPE;
    }

    // Returns the collision shape for entities
    @Override
    public VoxelShape getCollisionShape(@Nonnull BlockState state, @Nonnull BlockGetter level, @Nonnull BlockPos pos, @Nonnull CollisionContext context) {
        return GRAVESTONE_SHAPE;
    }

    // Indicates this block does not fill the full block space (for lighting)
    @Override
    public boolean useShapeForLightOcclusion(@Nonnull BlockState state) {
        return true;
    }

    // Returns the render shape for the block (model-based)
    @Override
    public RenderShape getRenderShape(@Nonnull BlockState state) {
        return RenderShape.MODEL;
    }

    // Creates a new block entity for the gravestone
    @Nullable
    @Override
    public BlockEntity newBlockEntity(@Nonnull BlockPos pos, @Nonnull BlockState state) {
        return new GravestoneBlockEntity(pos, state);
    }

    // Provides the ticker for the block entity to handle periodic tasks like sound effects
    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@Nonnull Level level, @Nonnull BlockState state, @Nonnull BlockEntityType<T> type) {
        if (level.isClientSide && type == com.nouho.soulgravestone.SoulGravestone.GRAVESTONE_BLOCK_ENTITY.get()) {
            return (BlockEntityTicker<T>) (BlockEntityTicker<GravestoneBlockEntity>) GravestoneBlockEntity::tick;
        }
        return null;
    }

    // Handles dropping items when the block is removed (not by player break)
    @Override
    public void onRemove(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState newState, boolean isMoving) {
        if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
            // Remove the ArmorStand name display
            com.nouho.soulgravestone.managers.GravestoneManager.removeGravestoneNameDisplay(level, pos);
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof GravestoneBlockEntity gravestoneBE) {
                for (ItemStack stack : gravestoneBE.getInventory()) {
                    if (!stack.isEmpty()) {
                        popResource(level, pos, stack);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    // Handles restoring items and XP to the player when the block is broken by a player
    @Override
    public BlockState playerWillDestroy(@Nonnull Level level, @Nonnull BlockPos pos, @Nonnull BlockState state, @Nonnull Player player) {
        if (!level.isClientSide) {
            // Remove the ArmorStand name display
            com.nouho.soulgravestone.managers.GravestoneManager.removeGravestoneNameDisplay(level, pos);
            // Restore all gravestone contents to player
            com.nouho.soulgravestone.managers.InventoryManager.restoreGravestoneContents(level, pos, player);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    // Adds the FACING property to the block state definition
    @Override
    protected void createBlockStateDefinition(@Nonnull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // Sets the block's facing direction when placed
    @Override
    public BlockState getStateForPlacement(@Nonnull BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // Spawns soul particles around the gravestone for atmospheric effect
    @Override
    public void animateTick(@Nonnull BlockState state, @Nonnull Level level, @Nonnull BlockPos pos, @Nonnull RandomSource random) {
        if (level.isClientSide) {
            // Spawn soul particles with some randomness (33% chance each tick)
            if (random.nextInt(3) == 0) {
                // Spawn particles around the block edges, slightly outside to avoid clipping
                double x = pos.getX() + random.nextDouble();
                double y = pos.getY() + random.nextDouble();
                double z = pos.getZ() + random.nextDouble();
                // Push particles slightly outside the block boundaries
                int face = random.nextInt(5);
                switch (face) {
                    case 0: // North face
                        z = pos.getZ() - 0.2;
                        break;
                    case 1: // South face
                        z = pos.getZ() + 1.2;
                        break;
                    case 2: // West face
                        x = pos.getX() - 0.2;
                        break;
                    case 3: // East face
                        x = pos.getX() + 1.2;
                        break;
                    case 5: // Top face
                        y = pos.getY() + 1.2;
                        break;
                }
                // Add slight floating motion toward the center and upward
                double motionX = (pos.getX() + 0.5 - x) * 0.01;
                double motionY = 0.02 + random.nextDouble() * 0.03;
                double motionZ = (pos.getZ() + 0.5 - z) * 0.01;
                level.addParticle(ParticleTypes.SOUL, x, y + 0.2, z, motionX, motionY, motionZ);
            }
        }
    }
}
