package com.ts.staircase_fantasy.client;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class VerticalBakedModel extends BakedModelWrapper<BakedModel> {
    private final Direction facing;
    private final Half half;
    private final StairsShape shape;

    private record QuadBucket(Map<Direction, List<BakedQuad>> culled, List<BakedQuad> unculled) {
        QuadBucket() {
            this(new EnumMap<>(Direction.class), new ArrayList<>());
            for (Direction d : Direction.values()) {
                culled.put(d, new ArrayList<>());
            }
        }
    }

    private final Map<RenderType, QuadBucket> staticQuadCache = new ConcurrentHashMap<>();
    private volatile QuadBucket fallbackStaticCache;

    public VerticalBakedModel(BakedModel sourceModel, Direction targetFacing, Half half, StairsShape shape) {
        super(sourceModel);
        this.facing = targetFacing;
        this.half = half;
        this.shape = shape;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return true;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        QuadBucket bucket;
        if (extraData != null && extraData != ModelData.EMPTY) {
            bucket = buildCleanProceduralBucket(state, rand, extraData, renderType);
        } else if (renderType != null) {
            bucket = staticQuadCache.computeIfAbsent(renderType, rt -> buildCleanProceduralBucket(state, rand, ModelData.EMPTY, rt));
        } else {
            if (fallbackStaticCache == null) {
                fallbackStaticCache = buildCleanProceduralBucket(state, rand, ModelData.EMPTY, null);
            }
            bucket = fallbackStaticCache;
        }

        return (side != null) ? bucket.culled().get(side) : bucket.unculled();
    }

    private QuadBucket buildCleanProceduralBucket(@Nullable BlockState state, RandomSource rand, ModelData extraData, @Nullable RenderType renderType) {
        QuadBucket bucket = new QuadBucket();

        Map<Direction, TextureAtlasSprite> faceSprites = new EnumMap<>(Direction.class);
        Map<Direction, Integer> faceTints = new EnumMap<>(Direction.class);
        TextureAtlasSprite fallbackSprite = originalModel.getParticleIcon(extraData);
        int fallbackTint = -1;

        for (Direction dir : Direction.values()) {
            List<BakedQuad> quads = originalModel.getQuads(state, dir, rand, extraData, renderType);
            if (!quads.isEmpty()) {
                faceSprites.put(dir, quads.get(0).getSprite());
                faceTints.put(dir, quads.get(0).getTintIndex());
                if (fallbackSprite == null) fallbackSprite = quads.get(0).getSprite();
                if (fallbackTint == -1) fallbackTint = quads.get(0).getTintIndex();
            }
        }
        for (BakedQuad q : originalModel.getQuads(state, null, rand, extraData, renderType)) {
            faceSprites.putIfAbsent(q.getDirection(), q.getSprite());
            faceTints.putIfAbsent(q.getDirection(), q.getTintIndex());
            if (fallbackSprite == null) fallbackSprite = q.getSprite();
            if (fallbackTint == -1) fallbackTint = q.getTintIndex();
        }

        List<int[]> boxes = new ArrayList<>();

        boxes.add(switch (facing) {
            case NORTH -> new int[]{0, 0, 0, 16, 16, 8};
            case SOUTH -> new int[]{0, 0, 8, 16, 16, 16};
            case WEST  -> new int[]{0, 0, 0, 8, 16, 16};
            case EAST  -> new int[]{8, 0, 0, 16, 16, 16};
            default    -> new int[]{0, 0, 0, 16, 16, 8};
        });

        if (shape == StairsShape.STRAIGHT) {
            boolean isLeft = (half == Half.TOP);
            boxes.add(getFrontVerticalPillar(facing, isLeft));
        } else if (shape == StairsShape.OUTER_LEFT || shape == StairsShape.OUTER_RIGHT) {
            boolean isLeft = (shape == StairsShape.OUTER_LEFT);
            boolean isTop = (half == Half.TOP);
            boxes.add(getFrontQuarterBox(facing, isLeft, isTop));
        } else {
            boolean isLeft = (shape == StairsShape.INNER_LEFT);
            boolean isTop = (half == Half.TOP);
            boxes.add(getFrontVerticalPillar(facing, isLeft));
            boxes.add(getFrontQuarterBox(facing, !isLeft, isTop));
        }

        for (int[] box : boxes) {
            emitBoxQuads(bucket, box, boxes, faceSprites, faceTints, fallbackSprite, fallbackTint);
        }

        return bucket;
    }

    private static void emitBoxQuads(QuadBucket bucket, int[] box, List<int[]> allBoxes,
                                     Map<Direction, TextureAtlasSprite> sprites,
                                     Map<Direction, Integer> tints,
                                     TextureAtlasSprite fallbackSprite, int fallbackTint) {
        float x0 = box[0] / 16.0f, y0 = box[1] / 16.0f, z0 = box[2] / 16.0f;
        float x1 = box[3] / 16.0f, y1 = box[4] / 16.0f, z1 = box[5] / 16.0f;

        for (Direction dir : Direction.values()) {
            if (isInternalSharedFace(box, dir, allBoxes)) {
                continue;
            }

            TextureAtlasSprite sprite = sprites.getOrDefault(dir, fallbackSprite);
            int tint = tints.getOrDefault(dir, fallbackTint);
            if (sprite == null) continue;

            float[][] corners = switch (dir) {
                case DOWN  -> new float[][]{{x0, y0, z1}, {x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}};
                case UP    -> new float[][]{{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                case NORTH -> new float[][]{{x1, y1, z0}, {x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}};
                case SOUTH -> new float[][]{{x0, y1, z1}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}};
                case WEST  -> new float[][]{{x0, y1, z0}, {x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}};
                case EAST  -> new float[][]{{x1, y1, z1}, {x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}};
            };

            int[] vertexData = buildCleanQuadVertices(corners, dir, sprite);
            BakedQuad quad = new BakedQuad(vertexData, tint, dir, sprite, true);

            boolean onOuterBoundary = switch (dir) {
                case DOWN  -> box[1] == 0;
                case UP    -> box[4] == 16;
                case NORTH -> box[2] == 0;
                case SOUTH -> box[5] == 16;
                case WEST  -> box[0] == 0;
                case EAST  -> box[3] == 16;
            };

            if (onOuterBoundary) {
                bucket.culled().get(dir).add(quad);
            } else {
                bucket.unculled().add(quad);
            }
        }
    }

    private static boolean isInternalSharedFace(int[] b, Direction dir, List<int[]> allBoxes) {
        for (int[] o : allBoxes) {
            if (o == b) continue;
            switch (dir) {
                case DOWN  -> { if (b[1] == o[4] && b[0] >= o[0] && b[3] <= o[3] && b[2] >= o[2] && b[5] <= o[5]) return true; }
                case UP    -> { if (b[4] == o[1] && b[0] >= o[0] && b[3] <= o[3] && b[2] >= o[2] && b[5] <= o[5]) return true; }
                case NORTH -> { if (b[2] == o[5] && b[0] >= o[0] && b[3] <= o[3] && b[1] >= o[1] && b[4] <= o[4]) return true; }
                case SOUTH -> { if (b[5] == o[2] && b[0] >= o[0] && b[3] <= o[3] && b[1] >= o[1] && b[4] <= o[4]) return true; }
                case WEST  -> { if (b[0] == o[3] && b[2] >= o[2] && b[5] <= o[5] && b[1] >= o[1] && b[4] <= o[4]) return true; }
                case EAST  -> { if (b[3] == o[0] && b[2] >= o[2] && b[5] <= o[5] && b[1] >= o[1] && b[4] <= o[4]) return true; }
            }
        }
        return false;
    }

    private static int[] buildCleanQuadVertices(float[][] corners, Direction dir, TextureAtlasSprite sprite) {
        int[] data = new int[32];
        float uMin = sprite.getU0();
        float uSpan = sprite.getU1() - uMin;
        float vMin = sprite.getV0();
        float vSpan = sprite.getV1() - vMin;

        int nx = (byte) (dir.getStepX() * 127) & 0xFF;
        int ny = (byte) (dir.getStepY() * 127) & 0xFF;
        int nz = (byte) (dir.getStepZ() * 127) & 0xFF;
        int packedNormal = (nz << 16) | (ny << 8) | nx;

        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            float px = corners[i][0];
            float py = corners[i][1];
            float pz = corners[i][2];

            float normU;
            float normV;
            switch (dir) {
                case NORTH -> { normU = 1.0f - px; normV = 1.0f - py; }
                case SOUTH -> { normU = px;        normV = 1.0f - py; }
                case WEST  -> { normU = pz;        normV = 1.0f - py; }
                case EAST  -> { normU = 1.0f - pz; normV = 1.0f - py; }
                case UP    -> { normU = px;        normV = pz; }
                case DOWN  -> { normU = px;        normV = 1.0f - pz; }
                default    -> { normU = px;        normV = 1.0f - py; }
            }

            data[offset]     = Float.floatToRawIntBits(px);
            data[offset + 1] = Float.floatToRawIntBits(py);
            data[offset + 2] = Float.floatToRawIntBits(pz);
            data[offset + 3] = 0xFFFFFFFF;
            data[offset + 4] = Float.floatToRawIntBits(uMin + normU * uSpan);
            data[offset + 5] = Float.floatToRawIntBits(vMin + normV * vSpan);
            data[offset + 6] = 0;
            data[offset + 7] = packedNormal;
        }
        return data;
    }

    private static int[] getFrontVerticalPillar(Direction facing, boolean isLeft) {
        return switch (facing) {
            case NORTH -> isLeft ? new int[]{0, 0, 8, 8, 16, 16} : new int[]{8, 0, 8, 16, 16, 16};
            case SOUTH -> isLeft ? new int[]{8, 0, 0, 16, 16, 8} : new int[]{0, 0, 0, 8, 16, 8};
            case WEST  -> isLeft ? new int[]{8, 0, 8, 16, 16, 16} : new int[]{8, 0, 0, 16, 16, 8};
            case EAST  -> isLeft ? new int[]{0, 0, 0, 8, 16, 8} : new int[]{0, 0, 8, 8, 16, 16};
            default    -> new int[]{0, 0, 8, 8, 16, 16};
        };
    }

    private static int[] getFrontQuarterBox(Direction facing, boolean isLeft, boolean isTop) {
        int y0 = isTop ? 8 : 0;
        int y1 = isTop ? 16 : 8;
        return switch (facing) {
            case NORTH -> isLeft ? new int[]{0, y0, 8, 8, y1, 16} : new int[]{8, y0, 8, 16, y1, 16};
            case SOUTH -> isLeft ? new int[]{8, y0, 0, 16, y1, 8} : new int[]{0, y0, 0, 8, y1, 8};
            case WEST  -> isLeft ? new int[]{8, y0, 8, 16, y1, 16} : new int[]{8, y0, 0, 16, y1, 8};
            case EAST  -> isLeft ? new int[]{0, y0, 0, 8, y1, 8} : new int[]{0, y0, 8, 8, y1, 16};
            default    -> new int[]{0, y0, 8, 8, y1, 16};
        };
    }
}