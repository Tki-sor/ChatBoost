package com.tkisor.chatboost.mixin.chat;

import com.mojang.authlib.GameProfile;
import com.tkisor.chatboost.ChatBoost;
import com.tkisor.chatboost.util.ChatUtils;
import com.tkisor.chatboost.util.SharedVariables;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.util.StringDecomposer;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.Instant;
import java.util.UUID;

@Mixin(ChatListener.class)
public abstract class MessageHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void cacheChatData(PlayerChatMessage message, GameProfile sender, ChatType.Bound params, CallbackInfo ci) {
        if( params.chatType().chat().translationKey().equals("chat.type.text") )
            SharedVariables.lastMsg = new ChatUtils.MessageData(sender, message.timeStamp(), true);
        else
            SharedVariables.lastMsg = ChatUtils.NIL_MSG_DATA;
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void cacheSystemData(Component message, boolean overlay, CallbackInfo ci) {


        String string = StringDecomposer.getPlainText(message);
        String name = ChatUtils.VANILLA_MESSAGE.matcher(string).matches() ? StringUtils.substringBetween(string, "<", ">") : null;
        UUID uuid = name == null ? Util.NIL_UUID : minecraft.getPlayerSocialManager().getDiscoveredUUID(name);

        SharedVariables.lastMsg = !uuid.equals(Util.NIL_UUID)
                ? new ChatUtils.MessageData(new GameProfile(uuid, name), Instant.now(), true)
                : ChatUtils.NIL_MSG_DATA;
    }
}
