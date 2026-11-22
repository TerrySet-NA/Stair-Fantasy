package com.ts.staircase_fantasy.mixin;

import com.ts.staircase_fantasy.client.StaircaseClientEvents;
import com.ts.staircase_fantasy.network.PlacementContextHolder;
import com.ts.staircase_fantasy.util.PlacementCalculator;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(BlockItem.class)
public abstract class BlockItemMixin {

    @ModifyVariable(method = "place", at = @At("HEAD"), argsOnly = true)
    private BlockPlaceContext overridePlacementContext(BlockPlaceContext context) {
        if (!(((BlockItem) (Object) this).getBlock() instanceof StairBlock stairBlock)) {
            return context;
        }
        Player player = context.getPlayer();
        if (player == null) {
            return context;
        }

        boolean isSpecial = player.level().isClientSide()
                ? StaircaseClientEvents.isSpecialModeEnabled()
                : PlacementContextHolder.isSpecialModeEnabled();
        if (!isSpecial) {
            return context;
        }

        PlacementCalculator.SelectionResult selection = PlacementCalculator.evaluate(
                context.getLevel(), player, stairBlock.defaultBlockState(), 1.0f
        );
        if (selection == null) {
            return context;
        }

        if (selection.panelFace() != context.getClickedFace()
                || !selection.renderBlockPos().equals(context.getClickedPos())) {
            BlockHitResult correctedHit = new BlockHitResult(
                    selection.exactIntersect(),
                    selection.panelFace(),
                    selection.renderBlockPos(),
                    false
            );
            return new BlockPlaceContext(
                    context.getLevel(),
                    player,
                    context.getHand(),
                    context.getItemInHand(),
                    correctedHit
            );
        }

        return context;
    }
}