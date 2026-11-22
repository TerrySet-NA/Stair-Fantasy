package com.ts.staircase_fantasy.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.ts.staircase_fantasy.StaircaseFantasy;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = StaircaseFantasy.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class StaircaseKeybinds {
    public static final KeyMapping TOGGLE_SPECIAL_MODE = new KeyMapping(
            "key.staircase_fantasy.toggle_mode",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            "key.categories.staircase_fantasy"
    );

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE_SPECIAL_MODE);
    }
}