package codes.nasty.mobslayer.block;

import codes.nasty.mobslayer.MobSlayer;
import codes.nasty.mobslayer.SlayerConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Attacks the mobs in front of its blade side ({@link #FACING}) with the damage and enchantments stored in its
 * block entity. {@link #ACTIVE} mirrors whether the redstone mode currently lets it run and spins the saw.
 */
public class MobSlayerBlock extends BaseEntityBlock {
	public static final MapCodec<MobSlayerBlock> CODEC = simpleCodec(MobSlayerBlock::new);
	/** The blade side; like a dispenser it faces the player who placed the block. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
	public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

	public MobSlayerBlock(Properties properties) {
		super(properties);
		this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ACTIVE, false));
	}

	@Override
	protected MapCodec<MobSlayerBlock> codec() {
		return CODEC;
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel && level.getBlockEntity(pos) instanceof MobSlayerBlockEntity slayer) {
			player.openMenu(slayer);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new MobSlayerBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
		return level.isClientSide() ? null : createTickerHelper(type, MobSlayer.MOB_SLAYER_BLOCK_ENTITY, MobSlayerBlockEntity::serverTick);
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, ACTIVE);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
		return this.defaultBlockState()
			.setValue(FACING, context.getNearestLookingDirection().getOpposite())
			.setValue(ACTIVE, SlayerConfig.get().defaultRedstoneMode().isActive(powered));
	}
}
