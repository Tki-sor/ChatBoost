package com.tkisor.chatboost.mixin.gui;

import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.tkisor.chatboost.ChatBoost;
import com.tkisor.chatboost.accessor.ChatHudAccessor;
import com.tkisor.chatboost.config.Config;
import com.tkisor.chatboost.data.ChatData;
import com.tkisor.chatboost.util.ChatUtils;
import com.tkisor.chatboost.util.Flags;
import net.minecraft.Util;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.tkisor.chatboost.ChatBoost.Logger;
import static com.tkisor.chatboost.ChatBoost.config;
import static com.tkisor.chatboost.util.SharedVariables.lastMsg;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements ChatHudAccessor {
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;
    @Shadow @Final private List<?> messageDeletionQueue;
    @Shadow private int chatScrollbarPos;


    @Shadow public abstract double getScale();
    @Shadow public abstract int getLinesPerPage();
    @Shadow protected abstract double screenToChatX(double x);
    @Shadow protected abstract double screenToChatY(double y);
    @Shadow protected abstract int getLineHeight();
    @Shadow protected abstract int getMessageLineIndexAt(double x, double y);
    @Shadow protected abstract void addMessage(Component component, @Nullable MessageSignature messageSignature, int i, @Nullable GuiMessageTag guiMessageTag, boolean bl);


    public List<GuiMessage> chatPatches$getMessages() { return allMessages; }
    public List<GuiMessage.Line> chatPatches$getVisibleMessages() { return trimmedMessages; }
    public int chatPatches$getScrolledLines() { return chatScrollbarPos; }
    public int chatPatches$getMessageLineIndex(double x, double y) { return getMessageLineIndexAt(x, y); }
    public double chatPatches$toChatLineX(double x) { return screenToChatX(x); }
    public double chatPatches$toChatLineY(double y) { return screenToChatY(y); }
    public int chatPatches$getLineHeight() { return getLineHeight(); }


    private static final Gson json = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(Component.class, (JsonSerializer<Component>) (src, type, context) -> Component.Serializer.toJsonTree(src))
            .registerTypeAdapter(Component.class, (JsonDeserializer<Component>) (json, type, context) -> Component.Serializer.fromJson(json))
            .registerTypeAdapter(Component.class, (InstanceCreator<Component>) type -> Component.empty())
            .create();


    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 31)
    private int moveChatText(int x) {
        return x - Mth.floor(10/this.getScale());
    }
    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 27)
    private int moveScrollBar(int af) {
        return af + Mth.floor(10/this.getScale());
    }
    // condensed to one method because the first part of both methods are practically identical
    @ModifyVariable(method = {"getMessageTagAt", "getClickedComponentStyleAt"}, argsOnly = true, at = @At("HEAD"), ordinal = 1)
    private double moveINDHoverText(double e) {
        return e + ( 10 * this.getScale() );
    }


    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            at = @At("HEAD"),
            argsOnly = true
    )
    private Component modifyMessage(Component message, Component m, MessageSignature messageSignature, int tick, @Nullable GuiMessageTag guiMessageTag, boolean bl) {
        if (bl || Flags.LOADING_CHATLOG.isRaised() || Flags.ADDING_CONDENSED_MESSAGE.isRaised())
            return message;

        final Style style = message.getStyle();
        boolean lastEmpty = lastMsg.equals(ChatUtils.NIL_MSG_DATA);

        Date now = lastEmpty ? new Date() : Date.from(lastMsg.timestamp());
        String nowTime = String.valueOf( now.getTime() );

        MutableComponent modified = Component.empty().setStyle(style);
        modified.append(
                config.time
                        ?config.makeTimestamp(now).setStyle( config.makeHoverStyle(now).withInsertion(nowTime) )
                        :Component.empty().setStyle(Style.EMPTY.withInsertion(nowTime))
        );
        modified.append(
                !lastEmpty && Config.getOption("chatNameFormat").changed() && lastMsg.vanilla()
                ? Component.empty().setStyle(style)
                        .append( config.formatPlayername( lastMsg.sender() ) )
                        .append(
                                Util.make(() -> {
                                    if (message.getContents() instanceof TranslatableContents ttc) {
                                        MutableComponent text = Component.empty().setStyle(style);
                                        List<Component> messages = Arrays.stream(ttc.getArgs()).map(arg -> (Component)arg).toList();

                                        for (int i = 1; i < messages.size(); i++) {
                                            text.append(messages.get(i));
                                        }

                                        return text;
                                    } else if (message.getContents() instanceof LiteralContents ltc) {
                                        String[] splitMessage = ltc.text().split(">");

                                        if(splitMessage.length > 1)
                                            // removes any preceding whitespace
                                            return Component.literal( splitMessage[1].replaceAll("^\\s+", "") ).setStyle(style);
                                        else
                                            //return Text.empty().setStyle(style); // use this? idk
                                            return message.plainCopy().setStyle(style);
                                    } else {
                                        return message.plainCopy().setStyle(style);
                                    }
                                })
                        )
                        .append(
                                Util.make(() -> {
                                    MutableComponent msg = Component.empty().setStyle(style);
                                    List<Component> siblings = message.getSiblings();
                                    int i = -1;

                                    // if the message uses the vanilla style but the main component doesn't have the full playername, then only add (the actual message) after it, (removes duped names)
                                    if(message.getContents() instanceof LiteralContents ltc && !ltc.text().contains(">"))
                                        i = siblings.stream().filter(sib -> sib.getString().contains(">")).mapToInt(siblings::indexOf).findFirst().orElse(i);

                                    // if the vanilla-style message is formatted weird, then only add the text *after* the first '>' (end of playername)
                                    if(i > -1) {
                                        Component rightTri = siblings.get(i);
                                        String rightTriStr = rightTri.getString();
                                        String restOfStr = rightTriStr.substring( rightTriStr.indexOf(">") + 1 ).replaceAll("^\\s+", "");
                                        // updates the sibling text and decrements the index, so it doesn't get skipped
                                        if(!restOfStr.isEmpty()) {
                                            siblings.set(i, Component.literal(restOfStr).setStyle(rightTri.getStyle()));
                                            --i;
                                        }
                                    }

                                    // if there was a split playername, add everything after the '>' (end of playername)
                                    // (if there wasn't a split playername, add everything [-1 + 1 = 0])
                                    // (if there was, only add after that part [i + 1 = after name component])
                                    for(int j = i + 1; j < siblings.size(); ++j)
                                        msg.append( siblings.get(j) );

                                    return msg;
                                })

                        ) : message

        );

            LocalDateTime dateTime = LocalDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault());
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        ChatData.getInstance().insert(json.toJson(modified, Component.class), dateTime.format(formatter));

        Logger.info(json.toJson(modified, Component.class));
        return modified;
    }
}
