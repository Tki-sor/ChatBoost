package com.tkisor.chatboost.gui;

import com.tkisor.chatboost.ChatBoost;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

public class SearchButtonWidget extends ImageButton {
    private final OnPress onLeftClick;
    private final OnPress onRightClick;

    public SearchButtonWidget(int x, int y, OnPress leftAction, OnPress rightAction) {
        super(x, y, 16, 16, 0, 0, 16, ResourceLocation.tryBuild(ChatBoost.MOD_ID, "textures/gui/search_buttons.png"), 16, 32, button -> {});
        this.onLeftClick = leftAction;
        this.onRightClick = rightAction;
    }

    @Override
    public boolean mouseClicked(double x, double y, int buttonType) {
        if(active && visible && clicked(x, y)) {
            if(buttonType == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onLeftClick.onPress(this);
                return true;
            } else if(buttonType == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                onRightClick.onPress(this);
                return true;
            }
        }
        return false;
    }
}
