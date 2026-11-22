package com.ts.staircase_fantasy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ts.staircase_fantasy.StaircaseFantasy;
import com.ts.staircase_fantasy.block.StaircaseProperties;
import com.ts.staircase_fantasy.network.SpecialUseItemOnPayload;
import com.ts.staircase_fantasy.util.PlacementCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

import java.util.Map;

@EventBusSubscriber(modid = StaircaseFantasy.MODID, value = Dist.CLIENT)
public class StaircaseClientEvents {

    private static boolean specialModeEnabled = false;

    public static boolean isSpecialModeEnabled() {
        return specialModeEnabled;
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        while (StaircaseKeybinds.TOGGLE_SPECIAL_MODE.consumeClick()) {
            specialModeEnabled = !specialModeEnabled;
            mc.player.displayClientMessage(
                    Component.translatable(specialModeEnabled
                            ? "message.staircase_fantasy.mode_enabled"
                            : "message.staircase_fantasy.mode_disabled"),
                    true
            );
        }
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide() || !specialModeEnabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof StairBlock stairBlock)) {
            return;
        }

        BlockState clickedState = mc.level.getBlockState(event.getPos());
        if (!mc.player.isShiftKeyDown() && clickedState.useWithoutItem(mc.level, mc.player, event.getHitVec()).consumesAction()) {
            return;
        }

        PacketDistributor.sendToServer(new SpecialUseItemOnPayload(
                event.getHand(),
                event.getHitVec(),
                specialModeEnabled
        ));

        // 因為原版伺服器端的 playSound(player, ...) 會刻意跳過發起動作的玩家本人，
        // 這裡比照原版 BlockItem#place 在客戶端本地為自己播放放置音效（其他玩家仍由伺服器廣播聽到）
        PlacementCalculator.SelectionResult selection = PlacementCalculator.evaluate(
                mc.level, mc.player, stairBlock.defaultBlockState(), 1.0f
        );
        if (selection != null) {
            BlockPos placePos = selection.placePos();
            if (mc.level.getBlockState(placePos).canBeReplaced()) {
                BlockState defaultState = stairBlock.defaultBlockState();
                SoundType soundType = defaultState.getSoundType(mc.level, placePos, mc.player);
                mc.level.playSound(
                        mc.player,
                        placePos,
                        soundType.getPlaceSound(),
                        SoundSource.BLOCKS,
                        (soundType.getVolume() + 1.0f) / 2.0f,
                        soundType.getPitch() * 0.8f
                );
            }
        }

        mc.player.swing(event.getHand());
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        Map<ModelResourceLocation, BakedModel> models = event.getModels();
        for (Block block : BuiltInRegistries.BLOCK) {
            if (!(block instanceof StairBlock)) continue;

            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                boolean isVertical = state.getValue(StaircaseProperties.VERTICAL);
                boolean isLocked = state.getValue(StaircaseProperties.LOCKED);
                if (!isVertical && !isLocked) continue;

                ModelResourceLocation targetLoc = BlockModelShaper.stateToModelLocation(state);
                if (!isVertical) {
                    BlockState vanillaState = state.setValue(StaircaseProperties.LOCKED, false);
                    BakedModel vanillaModel = models.get(BlockModelShaper.stateToModelLocation(vanillaState));
                    if (vanillaModel != null) {
                        models.put(targetLoc, vanillaModel);
                    }
                } else {
                    Direction facing = state.getValue(StairBlock.FACING);
                    Half half = state.getValue(StairBlock.HALF);
                    StairsShape shape = state.getValue(StairBlock.SHAPE);

                    Half sourceHalf = (shape == StairsShape.STRAIGHT) ? Half.BOTTOM : (half == Half.TOP ? Half.BOTTOM : Half.TOP);

                    BlockState northBaseState = block.defaultBlockState()
                            .setValue(StaircaseProperties.VERTICAL, false)
                            .setValue(StaircaseProperties.LOCKED, false)
                            .setValue(StairBlock.FACING, Direction.NORTH)
                            .setValue(StairBlock.HALF, sourceHalf)
                            .setValue(StairBlock.SHAPE, shape);

                    BakedModel northBaseModel = models.get(BlockModelShaper.stateToModelLocation(northBaseState));
                    if (northBaseModel != null) {
                        models.put(targetLoc, new VerticalBakedModel(northBaseModel, facing, half, shape));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !specialModeEnabled) return;

        if (!(mc.player.getMainHandItem().getItem() instanceof BlockItem blockItem)
                || !(blockItem.getBlock() instanceof StairBlock stairBlock)) {
            return;
        }

        BlockPos rawPos = event.getTarget().getBlockPos();
        if (mc.level.getBlockState(rawPos).canBeReplaced()) {
            event.setCanceled(true);
        }

        float partialTick = event.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        PlacementCalculator.SelectionResult selection = PlacementCalculator.evaluate(
                mc.level, mc.player, stairBlock.defaultBlockState(), partialTick
        );
        if (selection == null) return;

        BlockPos pos = selection.renderBlockPos();
        Direction face = selection.panelFace();
        Direction playerHoriz = selection.playerHorizontal();
        Vec3 cam = event.getCamera().getPosition();

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(pos.getX() - cam.x, pos.getY() - cam.y, pos.getZ() - cam.z);
        Matrix4f mat = poseStack.last().pose();

        MultiBufferSource.BufferSource bufferSource = mc.renderBuffers().bufferSource();

        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        float eps = 0.002f;
        float t1 = 1.0f / 3.0f;
        float t2 = 2.0f / 3.0f;

        VertexConsumer quadConsumer = bufferSource.getBuffer(RenderType.debugQuads());
        drawSelectedAreaFill(quadConsumer, mat, face, playerHoriz, eps, t1, t2, selection);
        drawThickGridLines(quadConsumer, mat, face, playerHoriz, eps, t1, t2, 0.012f);
        bufferSource.endBatch(RenderType.debugQuads());

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        poseStack.popPose();
    }

    private static void drawSelectedAreaFill(VertexConsumer vc, Matrix4f mat, Direction face, Direction playerHoriz,
                                             float eps, float t1, float t2, PlacementCalculator.SelectionResult sel) {
        int r, g, b, a = 115;
        switch (sel.zoneType()) {
            case CENTER -> { r = 255; g = 255; b = 255; a = 80; }
            case EDGE -> { r = 64; g = 185; b = 255; }
            case CORNER_OUTER_STAIR -> { r = 80; g = 230; b = 130; }
            case CORNER_INNER_STAIR -> { r = 255; g = 155; b = 50; }
            default -> { r = 255; g = 255; b = 255; }
        }

        int col = sel.col();
        int row = sel.row();
        float u0 = col == 0 ? 0.0f : (col == 1 ? t1 : t2);
        float u1 = col == 0 ? t1   : (col == 1 ? t2 : 1.0f);
        float v0 = row == 0 ? 0.0f : (row == 1 ? t1 : t2);
        float v1 = row == 0 ? t1   : (row == 1 ? t2 : 1.0f);

        if (col == 1 || row == 1) {
            emitQuadUV(vc, mat, face, playerHoriz, eps, u0, v0, u1, v0, u1, v1, u0, v1, r, g, b, a);
            return;
        }

        boolean outer = sel.isOuterCornerZone();
        if (col == 0 && row == 0) {
            if (outer) emitTriUV(vc, mat, face, playerHoriz, eps, 0, 0, t1, 0, 0, t1, r, g, b, a);
            else       emitTriUV(vc, mat, face, playerHoriz, eps, t1, 0, t1, t1, 0, t1, r, g, b, a);
        } else if (col == 2 && row == 0) {
            if (outer) emitTriUV(vc, mat, face, playerHoriz, eps, t2, 0, 1, 0, 1, t1, r, g, b, a);
            else       emitTriUV(vc, mat, face, playerHoriz, eps, t2, 0, 1, t1, t2, t1, r, g, b, a);
        } else if (col == 0 && row == 2) {
            if (outer) emitTriUV(vc, mat, face, playerHoriz, eps, 0, t2, t1, 1, 0, 1, r, g, b, a);
            else       emitTriUV(vc, mat, face, playerHoriz, eps, 0, t2, t1, t2, t1, 1, r, g, b, a);
        } else {
            if (outer) emitTriUV(vc, mat, face, playerHoriz, eps, t2, 1, 1, t2, 1, 1, r, g, b, a);
            else       emitTriUV(vc, mat, face, playerHoriz, eps, t2, t2, 1, t2, t2, 1, r, g, b, a);
        }
    }

    private static void drawThickGridLines(VertexConsumer vc, Matrix4f mat, Direction face, Direction playerHoriz,
                                           float eps, float t1, float t2, float thickness) {
        float[][] lines = {
            {0, 0, 1, 0}, {1, 0, 1, 1}, {1, 1, 0, 1}, {0, 1, 0, 0},
            {t1, 0, t1, 1}, {t2, 0, t2, 1},
            {0, t1, 1, t1}, {0, t2, 1, t2},
            {0, t1, t1, 0}, {t2, 0, 1, t1}, {0, t2, t1, 1}, {t2, 1, 1, t2}
        };

        float halfW = thickness * 0.5f;
        for (float[] line : lines) {
            float u1 = line[0], v1 = line[1];
            float u2 = line[2], v2 = line[3];
            float du = u2 - u1;
            float dv = v2 - v1;
            float len = (float) Math.sqrt(du * du + dv * dv);
            if (len < 1e-5f) continue;

            float nx = -dv / len * halfW;
            float ny = du / len * halfW;

            emitQuadUV(vc, mat, face, playerHoriz, eps,
                    u1 - nx, v1 - ny,
                    u1 + nx, v1 + ny,
                    u2 + nx, v2 + ny,
                    u2 - nx, v2 - ny,
                    255, 255, 255, 235);
        }
    }

    private static void emitTriUV(VertexConsumer vc, Matrix4f mat, Direction face, Direction playerHoriz, float eps,
                                  float u1, float v1, float u2, float v2, float u3, float v3,
                                  int r, int g, int b, int a) {
        emitQuadUV(vc, mat, face, playerHoriz, eps, u1, v1, u2, v2, u3, v3, u3, v3, r, g, b, a);
    }

    private static void emitQuadUV(VertexConsumer vc, Matrix4f mat, Direction face, Direction playerHoriz, float eps,
                                   float u1, float v1, float u2, float v2, float u3, float v3, float u4, float v4,
                                   int r, int g, int b, int a) {
        float[][] pts = {
            uvToFacePos(face, playerHoriz, u1, v1, eps),
            uvToFacePos(face, playerHoriz, u2, v2, eps),
            uvToFacePos(face, playerHoriz, u3, v3, eps),
            uvToFacePos(face, playerHoriz, u4, v4, eps)
        };
        for (int i = 0; i < 4; i++) {
            vc.addVertex(mat, pts[i][0], pts[i][1], pts[i][2]).setColor(r, g, b, a);
        }
    }

    private static float[] uvToFacePos(Direction face, Direction playerHoriz, float u, float v, float eps) {
        return switch (face) {
            case NORTH -> new float[]{1.0f - u, v, -eps};
            case SOUTH -> new float[]{u, v, 1.0f + eps};
            case WEST  -> new float[]{-eps, v, u};
            case EAST  -> new float[]{1.0f + eps, v, 1.0f - u};
            case UP -> switch (playerHoriz) {
                case NORTH -> new float[]{u, 1.0f + eps, 1.0f - v};
                case SOUTH -> new float[]{1.0f - u, 1.0f + eps, v};
                case WEST  -> new float[]{1.0f - v, 1.0f + eps, 1.0f - u};
                case EAST  -> new float[]{v, 1.0f + eps, u};
                default    -> new float[]{u, 1.0f + eps, 1.0f - v};
            };
            case DOWN -> switch (playerHoriz) {
                case NORTH -> new float[]{u, -eps, v};
                case SOUTH -> new float[]{1.0f - u, -eps, 1.0f - v};
                case WEST  -> new float[]{v, -eps, 1.0f - u};
                case EAST  -> new float[]{1.0f - v, -eps, u};
                default    -> new float[]{u, -eps, v};
            };
        };
    }
}