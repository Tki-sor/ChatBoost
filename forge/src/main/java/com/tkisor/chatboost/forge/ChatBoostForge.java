package com.tkisor.chatboost.forge;

import com.tkisor.chatboost.ChatBoost;
import dev.architectury.platform.forge.EventBuses;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@SuppressWarnings("removal")
@Mod(ChatBoost.MOD_ID)
public final class ChatBoostForge {
    public ChatBoostForge() {
        // Submit our event bus to let Architectury API register our content on the right time.
        EventBuses.registerModEventBus(ChatBoost.MOD_ID, FMLJavaModLoadingContext.get().getModEventBus());

        // Run our common setup.
        ChatBoost.init();

    }

}
