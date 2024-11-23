package com.tkisor.chatboost.mixin.security;

import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDeleteChatPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public abstract class CPNHMixin {
    /**
     * Prevents messages from being hidden.
     * Extremely unclear implementation on Mojang's part,
     * but based on how chat reports work, this is likely unwanted.
     */
    @Inject(method = "handleDeleteChat", at = @At("HEAD"), cancellable = true, require = 0)
    private void cancelDelMessage(ClientboundDeleteChatPacket clientboundDeleteChatPacket, CallbackInfo ci) {
//        if(config.chatHidePacket)
            ci.cancel();
    }
}
