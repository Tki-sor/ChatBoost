package com.tkisor.chatboost.mixin.gui;

import com.google.gson.*;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.tkisor.chatboost.ChatBoost;
import com.tkisor.chatboost.accessor.ChatHudAccessor;
import com.tkisor.chatboost.config.Config;
import com.tkisor.chatboost.data.ChatData;
import com.tkisor.chatboost.util.ChatUtils;
import com.tkisor.chatboost.util.Flags;
import com.tkisor.chatboost.util.SharedVariables;
import dev.architectury.platform.Platform;
import net.minecraft.Util;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.*;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.tkisor.chatboost.ChatBoost.Logger;
import static com.tkisor.chatboost.ChatBoost.config;
import static com.tkisor.chatboost.util.ChatUtils.OG_MSG_INDEX;
import static com.tkisor.chatboost.util.SharedVariables.lastMsg;

@Mixin(ChatComponent.class)
public abstract class ChatHudMixin implements ChatHudAccessor {
    @Shadow
    @Final
    private Minecraft minecraft;
    @Shadow
    @Final
    private List<GuiMessage> allMessages;
    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;
    @Shadow
    @Final
    private List<?> messageDeletionQueue;
    @Shadow
    private int chatScrollbarPos;


    @Shadow
    public abstract double getScale();

    @Shadow
    public abstract int getLinesPerPage();

    @Shadow
    protected abstract double screenToChatX(double x);

    @Shadow
    protected abstract double screenToChatY(double y);

    @Shadow
    protected abstract int getLineHeight();

    @Shadow
    protected abstract int getMessageLineIndexAt(double x, double y);

    @Shadow
    protected abstract void addMessage(Component component, @Nullable MessageSignature messageSignature, int i, @Nullable GuiMessageTag guiMessageTag, boolean bl);

    @Shadow public abstract void deleteMessage(MessageSignature arg);

    @Shadow public abstract void scrollChat(int i);

    @Shadow public abstract int getWidth();

    @Shadow private boolean newMessageSinceScroll;

    @Shadow public abstract void addMessage(Component arg);

    public List<GuiMessage> chatPatches$getMessages() {
        return allMessages;
    }

    public List<GuiMessage.Line> chatPatches$getVisibleMessages() {
        return trimmedMessages;
    }

    public int chatPatches$getScrolledLines() {
        return chatScrollbarPos;
    }

    public int chatPatches$getMessageLineIndex(double x, double y) {
        return getMessageLineIndexAt(x, y);
    }

    public double chatPatches$toChatLineX(double x) {
        return screenToChatX(x);
    }

    public double chatPatches$toChatLineY(double y) {
        return screenToChatY(y);
    }

    public int chatPatches$getLineHeight() {
        return getLineHeight();
    }


    private static final Gson json = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(Component.class, (JsonSerializer<Component>) (src, type, context) -> Component.Serializer.toJsonTree(src))
            .registerTypeAdapter(Component.class, (JsonDeserializer<Component>) (json, type, context) -> Component.Serializer.fromJson(json))
            .registerTypeAdapter(Component.class, (InstanceCreator<Component>) type -> Component.empty())
            .create();

    /**
     * Prevents the game from actually clearing chat history
     */
    @Inject(method = "clearMessages", at = @At("HEAD"))
    private void clear(boolean clearHistory, CallbackInfo ci) {
        // Clear message using F3+D
        if (!clearHistory) {
            minecraft.getChatListener().clearQueue();
            messageDeletionQueue.clear();
            allMessages.clear();
            trimmedMessages.clear();
            ChatData.getInstance().delete(ChatBoost.gameType, ChatBoost.gameName);
        }
    }

//    @ModifyExpressionValue(
//            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
//            at = @At(value = "CONSTANT", args = "intValue=100")
//    )
//    private int moreMessages(int hundred) {
//        return config.chatMaxMessages;
//    }

    /** allows for a chat width larger than 320px */
    @ModifyReturnValue(method = "getWidth()I", at = @At("RETURN"))
    private int moreWidth(int defaultWidth) {
        return config.chatWidth > 0 ? config.chatWidth : defaultWidth;
    }


    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 31)
    private int moveChatText(int x) {
        return x - Mth.floor(config.shiftChat / this.getScale());
    }

    @ModifyVariable(method = "render", at = @At(value = "STORE", ordinal = 0), index = 27)
    private int moveScrollBar(int af) {
        return af + Mth.floor(config.shiftChat / this.getScale());
    }

    // condensed to one method because the first part of both methods are practically identical
    @ModifyVariable(method = {"getMessageTagAt", "getClickedComponentStyleAt"}, argsOnly = true, at = @At("HEAD"), ordinal = 1)
    private double moveINDHoverText(double e) {
        return e + (config.shiftChat * this.getScale());
    }


    @ModifyVariable(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            at = @At("HEAD"),
            argsOnly = true,
            order = 1100
    )
    private Component modifyMessage(Component message, Component m, MessageSignature messageSignature, int tick, @Nullable GuiMessageTag guiMessageTag, boolean bl) {
        if (bl || Flags.LOADING_CHATLOG.isRaised() || Flags.ADDING_CONDENSED_MESSAGE.isRaised() || Flags.CHAT_DATA_LOADED.isRaised())
            return message;

        final Style style = message.getStyle();
        boolean lastEmpty = lastMsg.equals(ChatUtils.NIL_MSG_DATA);

        Date now = lastEmpty ? new Date() : Date.from(lastMsg.timestamp());
        String nowTime = String.valueOf(now.getTime());

        MutableComponent modified = Component.empty().setStyle(style);
        modified.append(
                config.time
                        ? config.makeTimestamp(now).setStyle(config.makeHoverStyle(now).withInsertion(nowTime))
                        : Component.empty().setStyle(Style.EMPTY.withInsertion(nowTime))
        );
        modified.append(
                !lastEmpty && Config.getOption("chatNameFormat").changed() && lastMsg.vanilla()
                        ? Component.empty().setStyle(style)
                        .append(config.formatPlayername(lastMsg.sender()))
                        .append(
                                Util.make(() -> {
                                    if (message.getContents() instanceof TranslatableContents ttc) {
                                        MutableComponent text = Component.empty().setStyle(style);
                                        List<Component> messages = Arrays.stream(ttc.getArgs()).map(arg -> (Component) arg).toList();

                                        for (int i = 1; i < messages.size(); i++) {
                                            text.append(messages.get(i));
                                        }

                                        return text;
                                    } else if (message.getContents() instanceof LiteralContents ltc) {
                                        String[] splitMessage = ltc.text().split(">");

                                        if (splitMessage.length > 1)
                                            // removes any preceding whitespace
                                            return Component.literal(splitMessage[1].replaceAll("^\\s+", "")).setStyle(style);
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
                                    if (message.getContents() instanceof LiteralContents ltc && !ltc.text().contains(">"))
                                        i = siblings.stream().filter(sib -> sib.getString().contains(">")).mapToInt(siblings::indexOf).findFirst().orElse(i);

                                    // if the vanilla-style message is formatted weird, then only add the text *after* the first '>' (end of playername)
                                    if (i > -1) {
                                        Component rightTri = siblings.get(i);
                                        String rightTriStr = rightTri.getString();
                                        String restOfStr = rightTriStr.substring(rightTriStr.indexOf(">") + 1).replaceAll("^\\s+", "");
                                        // updates the sibling text and decrements the index, so it doesn't get skipped
                                        if (!restOfStr.isEmpty()) {
                                            siblings.set(i, Component.literal(restOfStr).setStyle(rightTri.getStyle()));
                                            --i;
                                        }
                                    }

                                    // if there was a split playername, add everything after the '>' (end of playername)
                                    // (if there wasn't a split playername, add everything [-1 + 1 = 0])
                                    // (if there was, only add after that part [i + 1 = after name component])
                                    for (int j = i + 1; j < siblings.size(); ++j)
                                        msg.append(siblings.get(j));

                                    return msg;
                                })

                        ) : message

        );

        LocalDateTime dateTime = LocalDateTime.ofInstant(now.toInstant(), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        ChatData.getInstance().insert(json.toJson(modified, Component.class), dateTime.format(formatter));

        return modified;
    }

    private static Component lastMessage = null;
    @Inject(at = @At("HEAD"), method = "render")
    private void re(GuiGraphics guiGraphics, int i, int j, int k, CallbackInfo ci) {
        if (isChatFocused()) {
            if (allMessages.size() >= 100) {
                GuiMessage guiMessage = getCurrentPageMessages().get(10);
                int index = allMessages.indexOf(guiMessage);

                // new
                if (index <= 25) {
                    List<ChatData.MessageSql> forward;
                    if (lastMessage != null) {
                        forward = ChatData.getInstance().findMessages(ChatBoost.json.toJson(lastMessage, Component.class), "forward", 10);
                        if (forward.isEmpty()) lastMessage = null;
                    } else {
                        forward = ChatData.getInstance().findMessages(ChatBoost.json.toJson(allMessages.get(0).content(), Component.class), "forward", 10);
                    }
                    if (forward.isEmpty()) return;

                    lastMessage = forward.get(forward.size()-1).message().copy();
                    for (ChatData.MessageSql messageSql : forward) {
//                        Flags.ADDING_CONDENSED_MESSAGE.raise();
                        Flags.CHAT_DATA_LOADED.raise();
                        addMessage(messageSql.message(), null, minecraft.gui.getGuiTicks(), new GuiMessageTag(0x382fb5, null, null, "Restored"), false);
//                        Flags.ADDING_CONDENSED_MESSAGE.lower();
                        Flags.CHAT_DATA_LOADED.lower();

                        this.newMessageSinceScroll = true;
                        this.scrollChat(1);
                    }

                }

                // old
                if (index >= 75) {
                    List<ChatData.MessageSql> backward = ChatData.getInstance().findMessages(ChatBoost.json.toJson(allMessages.get(allMessages.size()-1).content(), Component.class), "backward", 10);
                    for (ChatData.MessageSql messageSql : backward) {
                        minecraft.getChatListener().removeFromDelayedMessageQueue(allMessages.get(0).signature());
                        messageDeletionQueue.remove(allMessages.get(0).signature());
                        allMessages.remove(allMessages.get(0));
                        trimmedMessages.remove(trimmedMessages.get(0));

                        addOldMessage(messageSql.message(), null, new GuiMessageTag(0x382fb5, null, null, "Restored"));
                        this.newMessageSinceScroll = true;
                        this.scrollChat(-1);
                    }

                }

            }


        }
    }

    public void addOldMessage(Component component) {
        this.addOldMessage(component, (MessageSignature)null, this.minecraft.isSingleplayer() ? GuiMessageTag.systemSinglePlayer() : GuiMessageTag.system());
    }

    public void addOldMessage(Component component, @Nullable MessageSignature messageSignature, @Nullable GuiMessageTag guiMessageTag) {
        int time = this.minecraft.gui.getGuiTicks();

        this.addOldMessage(component, messageSignature, time, guiMessageTag, false);
    }

    private void addOldMessage(Component component, @Nullable MessageSignature messageSignature, int i, @Nullable GuiMessageTag guiMessageTag, boolean bl) {
        int j = Mth.floor((double)this.getWidth() / this.getScale());
        if (guiMessageTag != null && guiMessageTag.icon() != null) {
            j -= guiMessageTag.icon().width + 4 + 2;
        }

        List<FormattedCharSequence> list = ComponentRenderUtils.wrapComponents(component, j, this.minecraft.font);
        boolean bl2 = this.isChatFocused();

        for (int k = 0; k < list.size(); ++k) {
            FormattedCharSequence formattedCharSequence = list.get(k);
            if (bl2 && this.chatScrollbarPos > 0) {
                this.newMessageSinceScroll = true;
                this.scrollChat(-1);
            }

            boolean bl3 = k == list.size() - 1;
            this.trimmedMessages.add(new GuiMessage.Line(i, formattedCharSequence, guiMessageTag, bl3));  // 将消息行添加到尾部
        }

        while (this.trimmedMessages.size() > 100) {
            this.trimmedMessages.remove(0);
        }

        if (!bl) {
            this.allMessages.add(new GuiMessage(i, component, messageSignature, guiMessageTag));  // 将整个消息添加到尾部

            while (this.allMessages.size() > 100) {
                this.allMessages.remove(0);
            }
        }
    }

    private boolean isChatFocused() {
        return this.minecraft.screen instanceof ChatScreen;
    }


    public List<GuiMessage> getCurrentPageMessages() {
        int linesPerPage = this.getLinesPerPage();
        int totalMessages = this.trimmedMessages.size();

        int startIndex = this.chatScrollbarPos;
        int endIndex = Math.min(startIndex + linesPerPage, totalMessages);

        return this.allMessages.subList(startIndex, endIndex);
    }

    @Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
    private void dontLogRestoredMessages(Component component, @Nullable GuiMessageTag guiMessageTag, CallbackInfo ci) {
        if( Flags.LOADING_CHATLOG.isRaised() && guiMessageTag != null )
            ci.cancel();
    }

    @Inject(
            method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;ILnet/minecraft/client/GuiMessageTag;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void addCounter(Component incoming, MessageSignature msd, int ticks, GuiMessageTag mi, boolean refreshing, CallbackInfo ci) {
        try {
            if( config.counter && !refreshing && !allMessages.isEmpty() && (!Flags.ADDING_CONDENSED_MESSAGE.isRaised() ) ) {
                // condenses the incoming message into the last message if it is the same
                if (Platform.isModLoaded("chatimage")) {
                    String json = ChatBoost.json.toJson(incoming, Component.class);
                    // 检查字符串中是否包含 "action": "show_chatimage"
                    if (json.contains("\"action\":\"show_chatimage\"")) {
                        return;
                    }
                }

                Component condensedLastMessage = ChatUtils.getCondensedMessage(incoming, 0);

                // if the counterCompact option is true but the last message received was not condensed, look for
                // any dupes in the last counterCompactDistance +1 messages and if any are found condense them
                if( config.counterCompact && condensedLastMessage.equals(incoming) ) {
                    // ensures {0 <= attemptDistance <= messages.size()} is true

                    int attemptDistance = Mth.clamp((
                            (config.counterCompactDistance == -1)
                                    ? allMessages.size()
                                    : (config.counterCompactDistance == 0)
                                    ? this.getLinesPerPage()
                                    : config.counterCompactDistance
                    ), 0, allMessages.size());

                    // exclude the first message, already checked above
                    allMessages.subList(1, attemptDistance)
                            .stream()
                            .filter( hudLine -> hudLine.content().getSiblings().get(OG_MSG_INDEX).getString().equalsIgnoreCase( incoming.getSiblings().get(OG_MSG_INDEX).getString() ) )
                            .findFirst()
                            .ifPresent( hudLine -> ChatUtils.getCondensedMessage(incoming, allMessages.indexOf(hudLine)) );
                }

                // if any message was condensed add it
                if( !condensedLastMessage.equals(incoming) || (config.counterCompact && condensedLastMessage.equals(incoming)) ) {
                    Flags.ADDING_CONDENSED_MESSAGE.raise();
                    addMessage( condensedLastMessage, msd, ticks, mi, false );
                    Flags.ADDING_CONDENSED_MESSAGE.lower();

                    ci.cancel();
                }
            }

        } catch(IndexOutOfBoundsException e) {
            Logger.error("[ChatHudMixin.addCounter] Couldn't add duplicate counter because message '{}' ({} parts) was not constructed properly.", incoming.getString(), incoming.getSiblings().size());
            Logger.error("[ChatHudMixin.addCounter] This could have also been caused by an issue with the new CompactChat dupe-condensing method.");
            Logger.error("[ChatHudMixin.addCounter] Either way, this was caused by a bug or mod incompatibility. Please report this on GitHub or on the Discord!", e);
        } catch(Exception e) {
            Logger.error("[ChatHudMixin.addCounter] /!\\ Couldn't add duplicate counter because of an unexpected error. Please report this on GitHub or on the Discord! /!\\", e);
        }


    }
}
