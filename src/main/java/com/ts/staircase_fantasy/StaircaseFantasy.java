package com.ts.staircase_fantasy;

import com.mojang.logging.LogUtils;
import com.ts.staircase_fantasy.network.PlacementContextHolder;
import com.ts.staircase_fantasy.network.SpecialUseItemOnPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;

@Mod(StaircaseFantasy.MODID)
public class StaircaseFantasy {
    public static final String MODID = "staircase_fantasy";
    public static final Logger LOGGER = LogUtils.getLogger();

    public StaircaseFantasy(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::registerPayloads);
        LOGGER.info("Staircase Fantasy initialized.");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
                SpecialUseItemOnPayload.TYPE,
                SpecialUseItemOnPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (!(context.player() instanceof ServerPlayer player)) return;

                    InteractionHand hand = payload.hand();
                    BlockHitResult hitResult = payload.hitResult();
                    ItemStack stack = player.getItemInHand(hand);

                    if (!player.canInteractWithBlock(hitResult.getBlockPos(), 1.5)) return;

                    PlacementContextHolder.set(payload.specialMode());
                    try {
                        player.gameMode.useItemOn(player, player.serverLevel(), stack, hand, hitResult);
                    } finally {
                        PlacementContextHolder.clear();
                    }
                })
        );
    }
}