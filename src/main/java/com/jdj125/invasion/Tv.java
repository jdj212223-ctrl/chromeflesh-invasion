package com.jdj125.invasion;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.HashMap;
import java.util.Map;

/**
 * An old TV. Right-click to turn it on (static) or off. Once the invasion has started, a TV that's on switches to the
 * emergency alert: the alert tone for a minute and the warning in chat for everyone nearby, then glitchy static.
 */
public class Tv extends HorizontalDirectionalBlock {
    public static final MapCodec<Tv> CODEC = simpleCodec(Tv::new);
    /** 0 off, 1 static, 2 emergency alert. */
    public static final IntegerProperty SCREEN = IntegerProperty.create("screen", 0, 2);
    private static final VoxelShape SHAPE = Block.box(1, 0, 3, 15, 13, 14);
    public static final int RANGE = 24, LOUD_TICKS = 60 * 20;
    /** When each alerting TV switched over (game time), for the minute of alert tones. */
    private static final Map<BlockPos, Long> ALERTED = new HashMap<>();

    public Tv(Properties props) {
        super(props);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(SCREEN, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, SCREEN);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        boolean on = state.getValue(SCREEN) == 0;
        level.setBlockAndUpdate(pos, state.setValue(SCREEN, on ? 1 : 0));
        level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 0.6f, on ? 1.4f : 0.8f);
        if (on) level.scheduleTick(pos, this, 20);
        else ALERTED.remove(pos);
        return InteractionResult.CONSUME;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moving) {
        if (state.getValue(SCREEN) > 0) level.scheduleTick(pos, this, 20);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        int screen = state.getValue(SCREEN);
        if (screen == 0) return;
        long now = level.getGameTime();
        if (screen == 1 && Invasion.started(level.getServer())) {
            level.setBlockAndUpdate(pos, state.setValue(SCREEN, 2));
            alert(level, pos, now);
        } else if (screen == 2) {
            long since = now - ALERTED.computeIfAbsent(pos.immutable(), p -> now);
            if (since < LOUD_TICKS) level.playSound(null, pos, Invasion.ALERT.get(), SoundSource.BLOCKS, 1.2f, 1);
            else if (random.nextInt(4) == 0) level.playSound(null, pos, Invasion.GLITCH.get(), SoundSource.BLOCKS, 0.4f, 0.7f + random.nextFloat() * 0.5f);
        }
        level.scheduleTick(pos, this, 60);
    }

    /** The broadcast: everyone near the TV reads the warning. */
    public static void alert(ServerLevel level, BlockPos pos, long now) {
        ALERTED.put(pos.immutable(), now);
        String robot = Invasion.glitch("robot");
        for (ServerPlayer p : level.getEntitiesOfClass(ServerPlayer.class, new net.minecraft.world.phys.AABB(pos).inflate(RANGE))) {
            p.displayClientMessage(Component.translatable("chromeflesh_invasion.alert.title").withStyle(ChatFormatting.RED, ChatFormatting.BOLD), false);
            p.displayClientMessage(Component.translatable("chromeflesh_invasion.alert.line1", robot).withStyle(ChatFormatting.GOLD), false);
            p.displayClientMessage(Component.translatable("chromeflesh_invasion.alert.line2").withStyle(ChatFormatting.YELLOW), false);
            p.displayClientMessage(Component.translatable("chromeflesh_invasion.alert.line3").withStyle(ChatFormatting.RED), false);
        }
        level.playSound(null, pos, Invasion.ALERT.get(), SoundSource.BLOCKS, 1.5f, 1);
    }
}
