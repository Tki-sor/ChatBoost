package com.tkisor.chatboost.mixin;

import com.tkisor.chatboost.ChatBoost;
import com.tkisor.chatboost.data.ChatData;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /**
     * Injects callbacks to game exit events so cached data can still be saved
     * <p>
     * 未理解为什么需要两个"INVOKE"
     */
    @Inject(method = "run", at = {
            @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;fillReport(Lnet/minecraft/CrashReport;)Lnet/minecraft/CrashReport;"
            ),
            @At(
                    value = "INVOKE_ASSIGN",
                    target = "Lnet/minecraft/client/Minecraft;fillReport(Lnet/minecraft/CrashReport;)Lnet/minecraft/CrashReport;"
            ),
            @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/Minecraft;emergencySave()V"
            )
    })
    private void saveChatlogOnCrash(CallbackInfo ci) {
        ChatData.getInstance().close();
//        ChatLog.serialize(true);
    }
}
