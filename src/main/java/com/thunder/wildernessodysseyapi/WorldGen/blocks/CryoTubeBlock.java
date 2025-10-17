package com.thunder.wildernessodysseyapi.WorldGen.blocks;

import com.thunder.wildernessodysseyapi.api.CryoSleepEvent;
import com.thunder.wildernessodysseyapi.item.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

import static com.thunder.wildernessodysseyapi.Core.ModConstants.MOD_ID;

/**
 * Block representing a cryo tube spawn point.
 */
public class CryoTubeBlock {
    /**
     * Registry for blocks.
     */
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MOD_ID);

    /**
     * Registry for block entities.
     */
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    /**
     * The cryo tube block instance.
     */
    public static final DeferredBlock<Block> CRYO_TUBE = registerBlock(
            () -> new BlockImpl(BlockBehaviour.Properties.of()
                    .strength(-1.0F, 3600000.0F)
                    .noLootTable()
                    .lightLevel(s -> 7)
                    .noOcclusion()
                    .noCollission()
                    .sound(SoundType.METAL)));

    /**
     * Block entity type for the cryo tube.
     */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<CryoTubeBlockEntity>> CRYO_TUBE_ENTITY =
            BLOCK_ENTITY_TYPES.register("cryo_tube",
                    () -> BlockEntityType.Builder.of(CryoTubeBlockEntity::new, CRYO_TUBE.get()).build(null));

    private static <T extends Block> DeferredBlock<T> registerBlock(Supplier<T> block) {
        DeferredBlock<T> toReturn = BLOCKS.register("cryo_tube", block);
        registerBlockItem(toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItem(DeferredBlock<T> block) {
        ModItems.ITEMS.register("cryo_tube", () -> new CryoTubeBlockItem(block.get(), new Item.Properties()));
    }

    /**
     * Register the block with the given event bus.
     */
    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
        BLOCK_ENTITY_TYPES.register(eventBus);
    }

    /**
     * Simple implementation that allows players to sleep inside the tube.
     */
    public static class BlockImpl extends Block implements EntityBlock {
        public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
        // Narrow voxel shape matching the tube's slender footprint.
        private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 48.0D, 16.0D);
        /**
         * After this many ticks (10 Minecraft days) tubes no longer function.
         */
        private static final long MAX_SLEEP_TICKS = 24000L * 10L;


        public BlockImpl(Properties properties) {
            super(properties);
            this.registerDefaultState(this.defaultBlockState().setValue(FACING, Direction.NORTH));
        }

        @Override
        public BlockState getStateForPlacement(BlockPlaceContext context) {
            return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        }

        @Override
        protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Override
        public BlockState rotate(BlockState state, Rotation rotation) {
            return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
        }

        @Override
        public BlockState mirror(BlockState state, Mirror mirror) {
            return state.rotate(mirror.getRotation(state.getValue(FACING)));
        }

        @Override
        public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return SHAPE;
        }

        @Override
        public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
            return SHAPE;
        }

        @Override
        public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
            return Shapes.empty();
        }

        // NeoForge 21 updated the Block interaction API. The previous
        // `use` method now resolves through `useItemOn` for item based
        // interactions or `useWithoutItem` when empty handed. Using the old
        // signature causes compilation errors. Implement the new
        // `useWithoutItem` variant to preserve the behaviour of letting the
        // player sleep in the tube.
        public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand) {
            if (level.getDayTime() >= MAX_SLEEP_TICKS) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.wildernessodysseyapi.cryo_tube_locked"), true);
                }
                return InteractionResult.FAIL;
            }

            if (!level.isClientSide) {
                player.startSleepInBed(pos);
                player.setPose(Pose.STANDING);
                Direction facing = state.getValue(FACING);
                float rotation = facing.toYRot();
                player.setYRot(rotation);
                player.setYBodyRot(rotation);
                player.setYHeadRot(rotation);
                player.setXRot(0.0F);
                if (player instanceof ServerPlayer serverPlayer) {
                    NeoForge.EVENT_BUS.post(new CryoSleepEvent(serverPlayer));
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new CryoTubeBlockEntity(pos, state);
        }
    }
}
