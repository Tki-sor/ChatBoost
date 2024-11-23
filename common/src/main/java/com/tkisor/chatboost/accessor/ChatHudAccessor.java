package com.tkisor.chatboost.accessor;

import com.tkisor.chatboost.mixin.gui.ChatHudMixin;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;

import java.util.List;

/**
 * An access-widening interface used with {@link ChatHudMixin}
 * to access necessary fields and methods w/o an extra
 * accessor mixin. To get an instance, use
 * {@link ChatHudAccessor#from(ChatComponent)} or
 * {@link ChatHudAccessor#from(Minecraft)}.
 */
public interface ChatHudAccessor {
    // these two methods avoid needing to cast everywhere because it looks ugly
    static ChatHudAccessor from(ChatComponent chatHud) {
        return ((ChatHudAccessor) chatHud);
    }
    static ChatHudAccessor from(Minecraft client) {
        return from(client.gui.getChat());
    }

    /** {@link ChatComponent#allMessages} */
    List<GuiMessage> chatPatches$getMessages();
    /** {@link ChatComponent#trimmedMessages} */
    List<GuiMessage.Line> chatPatches$getVisibleMessages();
    /** {@link ChatComponent#chatScrollbarPos} */
    int chatPatches$getScrolledLines();

    /** {@link ChatComponent#getMessageLineIndexAt(double, double)} */
    int chatPatches$getMessageLineIndex(double x, double y);
    /** {@link ChatComponent#screenToChatX(double)} */
    double chatPatches$toChatLineX(double x);
    /** {@link ChatComponent#screenToChatY(double)} */
    double chatPatches$toChatLineY(double y);
    /** {@link ChatComponent#getLineHeight()} */
    int chatPatches$getLineHeight();
}