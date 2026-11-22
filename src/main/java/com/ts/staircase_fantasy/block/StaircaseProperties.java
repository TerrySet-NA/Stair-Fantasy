package com.ts.staircase_fantasy.block;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class StaircaseProperties {
    public static final BooleanProperty VERTICAL = BooleanProperty.create("vertical");
    public static final BooleanProperty LOCKED = BooleanProperty.create("locked");

    public static VoxelShape getVerticalShape(Direction facing, Half half, StairsShape shape) {
        VoxelShape backWall = switch (facing) {
            case NORTH -> Block.box(0, 0, 0, 16, 16, 8);
            case SOUTH -> Block.box(0, 0, 8, 16, 16, 16);
            case WEST  -> Block.box(0, 0, 0, 8, 16, 16);
            case EAST  -> Block.box(8, 0, 0, 16, 16, 16);
            default    -> Block.box(0, 0, 0, 16, 16, 8);
        };

        if (shape == StairsShape.STRAIGHT) {
            boolean isLeft = (half == Half.TOP);
            VoxelShape pillar = getFrontHalfBox(facing, isLeft, true, true);
            return Shapes.or(backWall, pillar);
        }

        boolean isTop = (half == Half.TOP);
        if (shape == StairsShape.OUTER_LEFT) {
            return Shapes.or(backWall, getFrontQuarterBox(facing, true, isTop));
        }
        if (shape == StairsShape.OUTER_RIGHT) {
            return Shapes.or(backWall, getFrontQuarterBox(facing, false, isTop));
        }

        boolean isLeft = (shape == StairsShape.INNER_LEFT);
        VoxelShape sidePillar = getFrontHalfBox(facing, isLeft, true, true);
        VoxelShape horizBar = getFrontHalfBox(facing, false, false, isTop);
        return Shapes.or(backWall, sidePillar, horizBar);
    }

    private static VoxelShape getFrontQuarterBox(Direction facing, boolean isLeft, boolean isTop) {
        int y0 = isTop ? 8 : 0;
        int y1 = isTop ? 16 : 8;
        return switch (facing) {
            case NORTH -> isLeft ? Block.box(0, y0, 8, 8, y1, 16) : Block.box(8, y0, 8, 16, y1, 16);
            case SOUTH -> isLeft ? Block.box(8, y0, 0, 16, y1, 8) : Block.box(0, y0, 0, 8, y1, 8);
            case WEST  -> isLeft ? Block.box(8, y0, 8, 16, y1, 16) : Block.box(8, y0, 0, 16, y1, 8);
            case EAST  -> isLeft ? Block.box(0, y0, 0, 8, y1, 8) : Block.box(0, y0, 8, 8, y1, 16);
            default    -> Block.box(0, y0, 8, 8, y1, 16);
        };
    }

    private static VoxelShape getFrontHalfBox(Direction facing, boolean isLeft, boolean fullHeight, boolean isTop) {
        int y0 = fullHeight ? 0 : (isTop ? 8 : 0);
        int y1 = fullHeight ? 16 : (isTop ? 16 : 8);
        if (!fullHeight) {
            return switch (facing) {
                case NORTH -> Block.box(0, y0, 8, 16, y1, 16);
                case SOUTH -> Block.box(0, y0, 0, 16, y1, 8);
                case WEST  -> Block.box(8, y0, 0, 16, y1, 16);
                case EAST  -> Block.box(0, y0, 0, 8, y1, 16);
                default    -> Block.box(0, y0, 8, 16, y1, 16);
            };
        }
        return switch (facing) {
            case NORTH -> isLeft ? Block.box(0, 0, 8, 8, 16, 16) : Block.box(8, 0, 8, 16, 16, 16);
            case SOUTH -> isLeft ? Block.box(8, 0, 0, 16, 16, 8) : Block.box(0, 0, 0, 8, 16, 8);
            case WEST  -> isLeft ? Block.box(8, 0, 8, 16, 16, 16) : Block.box(8, 0, 0, 16, 16, 8);
            case EAST  -> isLeft ? Block.box(0, 0, 0, 8, 16, 8) : Block.box(0, 0, 8, 8, 16, 16);
            default    -> Block.box(0, 0, 8, 8, 16, 16);
        };
    }
}