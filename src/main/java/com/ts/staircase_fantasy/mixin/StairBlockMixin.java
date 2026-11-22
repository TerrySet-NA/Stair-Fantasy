package com.ts.staircase_fantasy.mixin;

import com.ts.staircase_fantasy.block.StaircaseProperties;
import com.ts.staircase_fantasy.client.StaircaseClientEvents;
import com.ts.staircase_fantasy.network.PlacementContextHolder;
import com.ts.staircase_fantasy.util.PlacementCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(StairBlock.class)
public abstract class StairBlockMixin extends Block {

    @Shadow @Final public static DirectionProperty FACING;
    @Shadow @Final public static EnumProperty<Half> HALF;
    @Shadow @Final public static EnumProperty<StairsShape> SHAPE;

    public StairBlockMixin(Properties properties) {
        super(properties);
    }

    @Inject(method = "<init>(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;)V", at = @At("TAIL"))
    private void onInit(BlockState baseState, BlockBehaviour.Properties properties, CallbackInfo ci) {
        this.registerDefaultState(this.defaultBlockState()
                .setValue(StaircaseProperties.VERTICAL, false)
                .setValue(StaircaseProperties.LOCKED, false));
    }

    @Inject(method = "createBlockStateDefinition", at = @At("TAIL"))
    private void onCreateBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder, CallbackInfo ci) {
        builder.add(StaircaseProperties.VERTICAL, StaircaseProperties.LOCKED);
    }

    @Inject(method = "getShape", at = @At("HEAD"), cancellable = true)
    private void onGetShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context, CallbackInfoReturnable<VoxelShape> cir) {
        if (state.getValue(StaircaseProperties.VERTICAL)) {
            cir.setReturnValue(StaircaseProperties.getVerticalShape(
                    state.getValue(FACING),
                    state.getValue(HALF),
                    state.getValue(SHAPE)
            ));
        }
    }

    @Inject(method = "updateShape", at = @At("HEAD"), cancellable = true)
    private void onUpdateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos, CallbackInfoReturnable<BlockState> cir) {
        if (state.getValue(StaircaseProperties.LOCKED)) {
            if (state.getValue(BlockStateProperties.WATERLOGGED)) {
                level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
            }
            cir.setReturnValue(state);
        }
    }

    @Inject(method = "isStairs", at = @At("HEAD"), cancellable = true)
    private static void onIsStairs(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (state.getBlock() instanceof StairBlock && state.getValue(StaircaseProperties.LOCKED)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "getStateForPlacement", at = @At("HEAD"), cancellable = true)
    private void onGetStateForPlacement(BlockPlaceContext context, CallbackInfoReturnable<BlockState> cir) {
        Player player = context.getPlayer();
        if (player == null) return;

        boolean isSpecial = player.level().isClientSide()
                ? StaircaseClientEvents.isSpecialModeEnabled()
                : PlacementContextHolder.isSpecialModeEnabled();
        if (!isSpecial) return;

        PlacementCalculator.SelectionResult selection = PlacementCalculator.evaluate(
                context.getLevel(), player, this.defaultBlockState(), 1.0f
        );
        if (selection == null) return;

        if (!selection.isVanillaFallback() && selection.customState() != null) {
            cir.setReturnValue(selection.customState());
        }
    }
}