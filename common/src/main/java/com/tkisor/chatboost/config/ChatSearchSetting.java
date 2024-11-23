package com.tkisor.chatboost.config;

// search settings

import com.tkisor.chatboost.mixin.gui.ChatScreenMixin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Represents a search setting pertaining to the {@link ChatScreenMixin} screen. */
public class ChatSearchSetting {
	public static ChatSearchSetting caseSensitive, modifiers, regex;

	public static boolean updateSearchColor = false;

	private static final Component TOGGLE_OFF = Component.nullToEmpty(": §7[§cX§4=§7]"), TOGGLE_ON = Component.nullToEmpty(": §7[§2=§aO§7]");

	public final Button button;
	public boolean on;
	private final Component name;

	public ChatSearchSetting(String key, boolean on, final int y, int yOffset) {
		this.name = Component.translatable("text.chatpatches.search." + key);
		this.on = on;


		Component text = name.copy().append( on ? TOGGLE_ON : TOGGLE_OFF );
		Button.OnPress UPDATE_BUTTON = button -> {
			this.on = !this.on;
			button.setMessage(this.name.copy().append(this.on ? TOGGLE_ON : TOGGLE_OFF));

			// flags ChatScreenMixin to update the search text color
			updateSearchColor = true;
		};

		this.button = Button.builder(text, UPDATE_BUTTON)
				.bounds(8, y + yOffset, Minecraft.getInstance().font.width(text.getVisualOrderText()) + 10, 20)
			.tooltip(Tooltip.create( Component.translatable("text.chatpatches.search.desc." + key) ))
			.build();
	}
}