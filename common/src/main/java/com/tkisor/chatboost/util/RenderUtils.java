package com.tkisor.chatboost.util;

import net.minecraft.client.GuiMessage;
import net.minecraft.network.chat.Component;

public class RenderUtils {
	public static final GuiMessage NIL_HUD_LINE = new GuiMessage(0, Component.empty(), null, null);


	public static class MousePos {
		public int x, y;

		private MousePos(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public static MousePos of(int x, int y) {
			return new MousePos(x, y);
		}
	}
}
