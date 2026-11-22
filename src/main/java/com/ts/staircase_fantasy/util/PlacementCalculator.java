package com.ts.staircase_fantasy.util;

import com.ts.staircase_fantasy.block.StaircaseProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;

public class PlacementCalculator {

    public enum ZoneType {
        CENTER,             // 中間（正中央）：完全原版規則
        EDGE,               // 側邊（上、下、左、右四邊）
        CORNER_OUTER_STAIR, // 外轉角（角落內側三角區 -> OUTER）
        CORNER_INNER_STAIR  // 內轉角（角落外側三角區 -> INNER）
    }

    public record SelectionResult(
            BlockPos renderBlockPos,
            Direction panelFace,
            Direction playerHorizontal,
            BlockPos placePos,
            Vec3 exactIntersect,
            ZoneType zoneType,
            int col,
            int row,
            boolean isOuterCornerZone,
            boolean isVanillaFallback,
            BlockState customState
    ) {}

    public static SelectionResult evaluate(Level level, Player player, BlockState defaultStairState, float partialTick) {
        double reach = player.blockInteractionRange();
        Vec3 eyePos = player.getEyePosition(partialTick);
        Vec3 lookVec = player.getViewVector(partialTick);
        Vec3 endPos = eyePos.add(lookVec.scale(reach));

        BlockHitResult hit = level.clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));

        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        BlockPos hitPos = hit.getBlockPos();
        BlockState hitState = level.getBlockState(hitPos);
        Direction playerHorizontal = player.getDirection();

        BlockHitResult fullBoxHit = Shapes.block().clip(eyePos, endPos, hitPos);

        Direction panelFace;
        Vec3 intersect;
        if (fullBoxHit != null) {
            panelFace = fullBoxHit.getDirection();
            intersect = fullBoxHit.getLocation();
        } else {
            panelFace = playerHorizontal.getOpposite();
            intersect = hit.getLocation();
        }

        BlockPos placePos = hitState.canBeReplaced() ? hitPos : hitPos.relative(panelFace);

        double[] uv = worldToPanelUV(hitPos, panelFace, playerHorizontal, intersect);
        double u = Math.max(0.0, Math.min(1.0, uv[0]));
        double v = Math.max(0.0, Math.min(1.0, uv[1]));

        int col = u < (1.0 / 3.0) ? 0 : (u > (2.0 / 3.0) ? 2 : 1); // 0:左, 1:中, 2:右
        int row = v < (1.0 / 3.0) ? 0 : (v > (2.0 / 3.0) ? 2 : 1); // 0:下, 1:中, 2:上

        BlockState baseState = defaultStairState
                .setValue(BlockStateProperties.WATERLOGGED, level.getFluidState(placePos).getType() == Fluids.WATER)
                .setValue(StaircaseProperties.LOCKED, true);

        // 1. 中間 (col == 1 && row == 1)：完全遵從原版角度、點擊位置與自動轉角連接
        if (col == 1 && row == 1) {
            return new SelectionResult(hitPos, panelFace, playerHorizontal, placePos, intersect, ZoneType.CENTER, col, row, false, true, null);
        }

        // 2. 側邊 (col == 1 || row == 1，排除正中央)
        if (col == 1 || row == 1) {
            if (panelFace.getAxis().isHorizontal()) {
                if (row == 1) {
                    Direction stairFacing = playerHorizontal;
                    Half sideHalf = (col == 0) ? Half.TOP : Half.BOTTOM; // TOP=左高柱, BOTTOM=右高柱

                    BlockState verticalState = baseState
                            .setValue(StaircaseProperties.VERTICAL, true)
                            .setValue(StairBlock.FACING, stairFacing)
                            .setValue(StairBlock.HALF, sideHalf)
                            .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);

                    return new SelectionResult(hitPos, panelFace, playerHorizontal, placePos, intersect, ZoneType.EDGE, col, row, false, false, verticalState);
                }
                return new SelectionResult(hitPos, panelFace, playerHorizontal, placePos, intersect, ZoneType.EDGE, col, row, false, true, null);
            } else {
                boolean isCeiling = (panelFace == Direction.DOWN);
                Direction forwardDir = isCeiling ? playerHorizontal.getOpposite() : playerHorizontal;
                Direction backwardDir = forwardDir.getOpposite();

                Direction dir;
                if (row == 2)      dir = forwardDir;
                else if (row == 0) dir = backwardDir;
                else if (col == 0) dir = playerHorizontal.getCounterClockWise();
                else               dir = playerHorizontal.getClockWise();

                Half half = isCeiling ? Half.TOP : Half.BOTTOM;

                BlockState groundDirState = baseState
                        .setValue(StaircaseProperties.VERTICAL, false)
                        .setValue(StairBlock.FACING, dir)
                        .setValue(StairBlock.HALF, half)
                        .setValue(StairBlock.SHAPE, StairsShape.STRAIGHT);

                return new SelectionResult(hitPos, panelFace, playerHorizontal, placePos, intersect, ZoneType.EDGE, col, row, false, false, groundDirState);
            }
        }

        // 3. 四個角落 (col != 1 && row != 1)：內側 = OUTER（外轉角），外側 = INNER（內轉角）
        double du = Math.abs(u - 0.5);
        double dv = Math.abs(v - 0.5);
        boolean isOuterZone = (du + dv) > (2.0 / 3.0);
        ZoneType zoneType = isOuterZone ? ZoneType.CORNER_INNER_STAIR : ZoneType.CORNER_OUTER_STAIR;

        boolean isVerticalWallCorner;
        Half half;
        Direction cornerFacing;
        StairsShape cornerShape;

        if (panelFace.getAxis().isVertical()) {
            isVerticalWallCorner = false;
            boolean isCeiling = (panelFace == Direction.DOWN);
            half = isCeiling ? Half.TOP : Half.BOTTOM;

            boolean isFrontRow = isCeiling ? (row == 0) : (row == 2);
            if (isFrontRow) {
                cornerFacing = playerHorizontal;
                if (col == 0) {
                    cornerShape = isOuterZone ? StairsShape.INNER_LEFT : StairsShape.OUTER_LEFT;
                } else {
                    cornerShape = isOuterZone ? StairsShape.INNER_RIGHT : StairsShape.OUTER_RIGHT;
                }
            } else {
                cornerFacing = playerHorizontal.getOpposite();
                if (col == 0) {
                    cornerShape = isOuterZone ? StairsShape.INNER_RIGHT : StairsShape.OUTER_RIGHT;
                } else {
                    cornerShape = isOuterZone ? StairsShape.INNER_LEFT : StairsShape.OUTER_LEFT;
                }
            }
        } else {
            isVerticalWallCorner = true;
            half = (row == 2) ? Half.TOP : Half.BOTTOM;
            cornerFacing = playerHorizontal;
            if (col == 0) {
                cornerShape = isOuterZone ? StairsShape.INNER_LEFT : StairsShape.OUTER_LEFT;
            } else {
                cornerShape = isOuterZone ? StairsShape.INNER_RIGHT : StairsShape.OUTER_RIGHT;
            }
        }

        BlockState cornerState = baseState
                .setValue(StaircaseProperties.VERTICAL, isVerticalWallCorner)
                .setValue(StairBlock.FACING, cornerFacing)
                .setValue(StairBlock.HALF, half)
                .setValue(StairBlock.SHAPE, cornerShape);

        return new SelectionResult(hitPos, panelFace, playerHorizontal, placePos, intersect, zoneType, col, row, isOuterZone, false, cornerState);
    }

    public static double[] worldToPanelUV(BlockPos pos, Direction face, Direction playerHorizontal, Vec3 worldPt) {
        double lx = worldPt.x - pos.getX();
        double ly = worldPt.y - pos.getY();
        double lz = worldPt.z - pos.getZ();

        return switch (face) {
            case NORTH -> new double[]{1.0 - lx, ly};
            case SOUTH -> new double[]{lx,       ly};
            case WEST  -> new double[]{lz,       ly};
            case EAST  -> new double[]{1.0 - lz, ly};
            case UP -> switch (playerHorizontal) {
                case NORTH -> new double[]{lx,       1.0 - lz};
                case SOUTH -> new double[]{1.0 - lx, lz};
                case WEST  -> new double[]{1.0 - lz, 1.0 - lx};
                case EAST  -> new double[]{lz,       lx};
                default    -> new double[]{lx, 1.0 - lz};
            };
            case DOWN -> switch (playerHorizontal) {
                case NORTH -> new double[]{lx,       lz};
                case SOUTH -> new double[]{1.0 - lx, 1.0 - lz};
                case WEST  -> new double[]{1.0 - lz, lx};
                case EAST  -> new double[]{lz,       1.0 - lx};
                default    -> new double[]{lx, lz};
            };
        };
    }
}