package com.createnewera.computer;

import java.util.ArrayList;
import java.util.List;

import com.createnewera.bus.BusPort;
import com.createnewera.bus.PartGeometry;
import com.createnewera.registry.ModBlockEntities;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.kinetics.base.HorizontalKineticBlock;
import com.simibubi.create.foundation.block.IBE;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/**
 * Runs on rotational force like any other Create machine: a shaft goes into its back, and the
 * faster it spins, the more of each tick its program gets.
 */
public class BrassComputerBlock extends HorizontalKineticBlock implements IBE<BrassComputerBlockEntity> {
    /** Lights the screen up; mirrors whether a program is loaded. */
    public static final BooleanProperty RUNNING = BooleanProperty.create("running");
    /** Stress per RPM, as Create counts it. */
    public static final double STRESS_IMPACT = 2.0;

    /**
     * The sockets for its own four ports, two on each side, as the model draws them facing
     * north: the middle of each, in pixels.
     */
    private static final int[][] SOCKETS = {{16, 8, 4}, {16, 8, 12}, {0, 8, 4}, {0, 8, 12}};

    /** Where one of the computer's own ports goes: the face, and the spot on its grid. */
    public record Socket(Direction face, int u, int v) {
        public boolean takenBy(BusPort part) {
            return part.overlaps(face, u, v, BusPort.Kind.PORT.size);
        }

        /** Whether a part of this size at another spot would cover any of the socket. */
        public boolean overlaps(Socket other, int size) {
            int own = BusPort.Kind.PORT.size;
            return face == other.face && u < other.u + size && other.u < u + own && v < other.v + size && other.v < v + own;
        }
    }

    public static List<Socket> sockets(BlockState state, BlockPos pos) {
        // quarter turns clockwise (seen from above) from facing north, as the blockstate turns the model
        int turns = (state.getValue(HORIZONTAL_FACING).get2DDataValue() + 2) % 4;
        List<Socket> out = new ArrayList<>(SOCKETS.length);
        for (int[] socket : SOCKETS) {
            double x = socket[0], z = socket[2];
            Direction face = x == 16 ? Direction.EAST : Direction.WEST;
            for (int i = 0; i < turns; i++) {
                double turned = 16 - z;
                z = x;
                x = turned;
                face = face.getClockWise();
            }
            Vec3 middle = new Vec3(pos.getX() + x / 16, pos.getY() + socket[1] / 16.0, pos.getZ() + z / 16);
            int[] spot = PartGeometry.spotAt(pos, face, middle, BusPort.Kind.PORT.size);
            out.add(new Socket(face, spot[0], spot[1]));
        }
        return out;
    }

    public BrassComputerBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(RUNNING, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(RUNNING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // like Create's cuckoo clock: back towards a neighbouring shaft if there is one, else face the player
        Direction preferred = getPreferredHorizontalFacing(context);
        Direction facing = preferred != null ? preferred.getOpposite() : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(HORIZONTAL_FACING, facing);
    }

    @Override
    public boolean hasShaftTowards(LevelReader level, BlockPos pos, BlockState state, Direction face) {
        return face == state.getValue(HORIZONTAL_FACING).getOpposite();
    }

    @Override
    public Direction.Axis getRotationAxis(BlockState state) {
        return state.getValue(HORIZONTAL_FACING).getAxis();
    }

    @Override
    public Class<BrassComputerBlockEntity> getBlockEntityClass() {
        return BrassComputerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends BrassComputerBlockEntity> getBlockEntityType() {
        return ModBlockEntities.BRASS_COMPUTER.get();
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        // let the wrench rotate us instead of opening the editor
        if (AllItems.WRENCH.isIn(stack) || stack.is(Tags.Items.TOOLS_WRENCH))
            return ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION;
        return super.useItemOn(stack, state, level, pos, player, hand, hit);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide)
            return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer serverPlayer)
            withBlockEntityDo(level, pos, computer -> computer.openEditor(serverPlayer));
        return InteractionResult.CONSUME;
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        // direction points from the neighbour back at us, so the signal leaves through its opposite
        return level.getBlockEntity(pos) instanceof BrassComputerBlockEntity computer
                ? computer.getOutput(direction.getOpposite())
                : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return getSignal(state, level, pos, direction);
    }
}
