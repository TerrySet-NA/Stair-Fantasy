package com.ts.staircase_fantasy.network;

import com.ts.staircase_fantasy.StaircaseFantasy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;

public record SpecialUseItemOnPayload(
        InteractionHand hand,
        BlockHitResult hitResult,
        boolean specialMode
) implements CustomPacketPayload {

    public static final Type<SpecialUseItemOnPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(StaircaseFantasy.MODID, "special_use_item_on"));

    public static final StreamCodec<FriendlyByteBuf, SpecialUseItemOnPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL.map(b -> b ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, h -> h == InteractionHand.MAIN_HAND),
            SpecialUseItemOnPayload::hand,
            StreamCodec.of(FriendlyByteBuf::writeBlockHitResult, FriendlyByteBuf::readBlockHitResult),
            SpecialUseItemOnPayload::hitResult,
            ByteBufCodecs.BOOL,
            SpecialUseItemOnPayload::specialMode,
            SpecialUseItemOnPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}