package com.tkisor.chatboost.mixin.gui;

import com.google.common.collect.Lists;
import com.llamalad7.mixinextras.injector.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.tkisor.chatboost.ChatBoost;
import com.tkisor.chatboost.accessor.ChatHudAccessor;
import com.tkisor.chatboost.config.ChatSearchSetting;
import com.tkisor.chatboost.data.ChatData;
import com.tkisor.chatboost.gui.MenuButtonWidget;
import com.tkisor.chatboost.gui.SearchButtonWidget;
import com.tkisor.chatboost.util.ChatUtils;
import com.tkisor.chatboost.util.RenderUtils;
import com.tkisor.chatboost.util.StringTextUtils;
import dev.architectury.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.tkisor.chatboost.ChatBoost.config;
import static com.tkisor.chatboost.config.ChatSearchSetting.*;
import static com.tkisor.chatboost.gui.MenuButtonWidget.anchor;
import static com.tkisor.chatboost.gui.MenuButtonWidget.of;
import static com.tkisor.chatboost.util.RenderUtils.NIL_HUD_LINE;
import static net.minecraft.network.chat.HoverEvent.Action.SHOW_ENTITY;
import static net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT;


@SuppressWarnings("JavadocReference")
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen {
    // search text
    @Unique
    private static final String SUGGESTION = Component.translatable("text.chatpatches.search.suggestion").getString();
    @Unique private static final Component SEARCH_TOOLTIP = Component.translatable("text.chatpatches.search.desc");
    // copy menu text
    @Unique private static final Component COPY_MENU_STRING = Component.translatable("text.chatpatches.copy.copyString");
    @Unique private static final Component COPY_RAW_STRING = Component.translatable("text.chatpatches.copy.rawString");
    @Unique private static final Component COPY_FORMATTED_STRING = Component.translatable("text.chatpatches.copy.formattedString");
    @Unique private static final Component COPY_JSON_STRING = Component.translatable("text.chatpatches.copy.jsonString");
    @Unique private static final Component COPY_MENU_TIMESTAMP = Component.translatable("text.chatpatches.copy.timestamp");
    @Unique private static final Component COPY_TIMESTAMP_TEXT = Component.translatable("text.chatpatches.copy.timestampText");
    @Unique private static final Component COPY_TIMESTAMP_HOVER_TEXT = Component.translatable("text.chatpatches.copy.timestampHoverText");
    @Unique private static final Component COPY_UNIX = Component.translatable("text.chatpatches.copy.unix");
    @Unique private static final Component COPY_MENU_LINKS = Component.translatable("text.chatpatches.copy.links");
    @Unique private static final Function<Integer, Component> COPY_LINK_N = (n) -> Component.translatable("text.chatpatches.copy.linkN", n);
    @Unique private static final Component COPY_MENU_SENDER = Component.translatable("text.chatpatches.copy.sender");
    @Unique private static final Component COPY_NAME = Component.translatable("text.chatpatches.copy.name");
    @Unique private static final Component COPY_UUID = Component.translatable("text.chatpatches.copy.uuid");
    @Unique private static final Component COPY_MENU_REPLY = Component.translatable("text.chatpatches.copy.reply");
    // coordinates and positioning
    @Unique private static final int SEARCH_X = 22, SEARCH_Y_OFFSET = -31, SEARCH_H = 12;
    @Unique private static final double SEARCH_W_MULT = 0.25;
    @Unique private static final int MENU_WIDTH = 146, MENU_HEIGHT = 76;
    @Unique private static final int MENU_X = 2, MENU_Y_OFFSET = SEARCH_Y_OFFSET - MENU_HEIGHT - 6;

    // search stuff
    @Unique private static boolean showSearch = true;
    @Unique private static boolean showSettingsMenu = false; // note: doesn't really need to be static
    // copy menu stuff
    @Unique private static boolean showCopyMenu = false; // true when a message was right-clicked on
    @Unique private static GuiMessage selectedLine = NIL_HUD_LINE;
    @Unique private static Map<Component, MenuButtonWidget> mainButtons = new LinkedHashMap<>(); // buttons that appear on the initial click
    @Unique private static Map<Component, MenuButtonWidget> hoverButtons = new LinkedHashMap<>(); // buttons that are revealed on hover
    @Unique private static List<GuiMessage.Line> hoveredVisibles = new ArrayList<>();
    // drafting (todo: can we remove these and instead use `originalChatText` and `lastSearch`?)
    @Unique private static String searchDraft = "";
    @Unique private static String messageDraft = "";

    @Unique private EditBox searchField;
    @Unique private SearchButtonWidget searchButton;
    @Unique private String lastSearch;
    @Unique private PatternSyntaxException searchError;

    @Shadow protected EditBox input;
    @Shadow private String initial;

    protected ChatScreenMixin(Component title) { super(title); }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void chatScreenInit(String initial, CallbackInfo ci) {
        if(config.messageDrafting && !messageDraft.isBlank()) {
            // if message drafting is enabled, a draft exists, and SMWYG sent an item message, clear the draft to avoid crashing
            if(Platform.isModLoaded("smwyg") && initial.matches("^\\[[\\w\\s]+]$"))
                messageDraft = initial;
                // otherwise if message drafting is enabled, a draft exists and this is not triggered by command key, update the draft
            else if(!initial.equals("/"))
                this.initial = messageDraft;
        }
    }

    /**
     * @implNote
     * <ol>
     *     <li>Initialize the search button</li>
     *     <li>Initialize the search field</li>
     *     <li>Initialize the setting options</li>
     *     <li>If {@link Config#hideSearchButton} is true, hide the search widgets</li>
     *     <li>If the copy menu hasn't already been initialized:</li>
     *     <ol>
     *     		<li>Initialize the hover buttons in {@link #hoverButtons}</li>
     *     		<li>Initialize the main buttons in {@link #mainButtons}</li>
     *     		<li>Increases the width for the {@link #COPY_MENU_SENDER} and {@link #COPY_MENU_REPLY} so there is more room for the player head icon</li>
     *     		<li>Updates the width of all {@link #mainButtons} to be the same max length, and updates all {@link #hoverButtons} to have the same x offset</li>
     *     </ol>
     * </ol>
     */
    @SuppressWarnings("JavadocReference")
    @Inject(method = "init", at = @At("TAIL"))
    protected void initSearchStuff(CallbackInfo ci) {
        searchButton = new SearchButtonWidget(2, height - 35, me -> showSearch = !showSearch, me -> showSettingsMenu = !showSettingsMenu);
        searchButton.setTooltip(Tooltip.create(SEARCH_TOOLTIP));

        searchField = new EditBox(minecraft.font, SEARCH_X, height + SEARCH_Y_OFFSET, (int)(width * SEARCH_W_MULT), SEARCH_H, Component.translatable("chat.editBox"));
        searchField.setMaxLength(384);
        searchField.setBordered(false);
        searchField.setSuggestion(SUGGESTION);
        searchField.setResponder(this::onSearchFieldUpdate);
        if(config.searchDrafting)
            searchField.setValue(searchDraft);

        final int yPos = height + (MENU_Y_OFFSET / 2) - 51; // had to extract here cause of mixin restrictions
        caseSensitive = new ChatSearchSetting("caseSensitive", true, yPos, 0);
        modifiers = new ChatSearchSetting("modifiers", false, yPos, 22);
        regex = new ChatSearchSetting("regex", false, yPos, 44);

        if(!config.hideSearchButton) {
            addWidget(searchField);
            addRenderableWidget(searchButton);
        }


        // only render all this menu stuff if it hasn't already been initialized
        if(!showCopyMenu) {
            // hover menu buttons, column two
            hoverButtons.put(COPY_RAW_STRING, of(1, COPY_RAW_STRING, () -> selectedLine.content().getString()));
            hoverButtons.put(COPY_FORMATTED_STRING, of(1, COPY_FORMATTED_STRING, () -> StringTextUtils.reorder( selectedLine.content().getVisualOrderText(), true )));
            hoverButtons.put(COPY_JSON_STRING, of(1, COPY_JSON_STRING, () -> Component.Serializer.toJson(selectedLine.content())));
            hoverButtons.put(COPY_LINK_N.apply(0), of(1, COPY_LINK_N.apply(0), () -> ""));
            hoverButtons.put(COPY_TIMESTAMP_TEXT, of(1, COPY_TIMESTAMP_TEXT, () -> selectedLine.content().getSiblings().get(ChatUtils.TIMESTAMP_INDEX).getString()));
            hoverButtons.put(COPY_TIMESTAMP_HOVER_TEXT, of(1, COPY_TIMESTAMP_HOVER_TEXT, () -> {
                HoverEvent hoverEvent = selectedLine.content().getSiblings().get(ChatUtils.TIMESTAMP_INDEX).getStyle().getHoverEvent();
                if(hoverEvent != null)
                    return hoverEvent.getValue(SHOW_TEXT).getString();
                else
                    return "";
            }));
            hoverButtons.put(COPY_NAME, of(1, COPY_NAME, () -> {
                Component message = selectedLine.content().getSiblings().get(ChatUtils.OG_MSG_INDEX);
                Component text = message.getSiblings().size() > ChatUtils.MSG_NAME_INDEX ? message.getSiblings().get(ChatUtils.MSG_NAME_INDEX) : Component.empty();
                HoverEvent.EntityTooltipInfo player = text.getStyle().getHoverEvent() != null ? text.getStyle().getHoverEvent().getValue(SHOW_ENTITY) : null;
                return player != null ? player.name.getString() : text.getString();
            }));
            hoverButtons.put(COPY_UUID, of(1, COPY_UUID, () -> {
                Component message = selectedLine.content().getSiblings().get(ChatUtils.OG_MSG_INDEX);
                Component text = message.getSiblings().size() > ChatUtils.MSG_NAME_INDEX ? message.getSiblings().get(ChatUtils.MSG_NAME_INDEX) : Component.empty();
                HoverEvent.EntityTooltipInfo player = text.getStyle().getHoverEvent() != null ? text.getStyle().getHoverEvent().getValue(SHOW_ENTITY) : null;
                return player != null ? player.id.toString() : text.getString();
            }));

            // main menu buttons, column one
            mainButtons.put(COPY_MENU_STRING, of(0, COPY_MENU_STRING, hoverButtons.get(COPY_RAW_STRING), hoverButtons.get(COPY_FORMATTED_STRING), hoverButtons.get(COPY_JSON_STRING)));
            mainButtons.put(COPY_MENU_LINKS, of(0, COPY_MENU_LINKS, hoverButtons.get(COPY_LINK_N.apply(0))));
            mainButtons.put(COPY_MENU_TIMESTAMP, of(0, COPY_MENU_TIMESTAMP, hoverButtons.get(COPY_TIMESTAMP_TEXT), hoverButtons.get(COPY_TIMESTAMP_HOVER_TEXT)));
            mainButtons.put(COPY_UNIX, of(0, COPY_UNIX, () -> {
                List<Component> siblings = selectedLine.content().getSiblings();
                String time = siblings.size() > ChatUtils.TIMESTAMP_INDEX ? siblings.get(ChatUtils.TIMESTAMP_INDEX).getStyle().getInsertion() : null;
                return time != null && !time.isEmpty() ? time : "?";
            }));
            mainButtons.put(COPY_MENU_SENDER, of(0, COPY_MENU_SENDER, hoverButtons.get(COPY_NAME), hoverButtons.get(COPY_UUID)));
            mainButtons.put(COPY_MENU_REPLY, of(0, COPY_MENU_REPLY, () -> "").setOtherPressAction(menuButton ->
                    input.setValue( StringTextUtils.fillVars(config.copyReplyFormat, hoverButtons.get(COPY_NAME).copySupplier.get())
                    )));

            // these two get extra width for the player head icon that renders, so it has enough space
            //mainButtons.get(COPY_MENU_SENDER).setWidth( mainButtons.get(COPY_MENU_SENDER).width + 20*2 );
            //mainButtons.get(COPY_MENU_REPLY).setWidth( mainButtons.get(COPY_MENU_REPLY).width + 20*2 );
            // updates the main buttons' widths and the hover buttons' x offsets to be the same, correct width to stack and align properly
            int mainWidth = mainButtons.values().stream().mapToInt(button -> button.width).max().getAsInt() - 2 * MenuButtonWidget.padding;
            mainWidth = Math.max(mainWidth, minecraft.font.width(COPY_MENU_SENDER) + 20*2);
            int mW = mainWidth;
            mainButtons.values().forEach(menuButton -> menuButton.setWidth(mW));
            hoverButtons.values().forEach(menuButton -> menuButton.xOffset = mainButtons.get(COPY_MENU_STRING).width);
        }
    }


    /**
     * @implNote Rendering order:
     * <ol>
     *     <li>The {@link #searchButton}</li>
     *     <li>If the search bar should show:</li>
     *     <ol>
     *     		<li>The {@link #searchField} background</li>
     *     		<li>The {@link #searchField} itself</li>
     *     		<li>If it isn't null, the {@link #searchError}</li>
     *     </ol>
     *     <li>If the settings menu should show:</li>
     *     <ol>
     *     		<li>The settings menu background</li>
     *     		<li>The setting buttons themselves</li>
     *     </ol>
     *     <li>If the copy menu has been loaded and should show:</li>
     *     <ol>
     *     		<li>The outline around the selected chat message</li>
     *     		<li>The copy menu buttons (the menu itself)</li>
     *     </ol>
     * </ol>
     */
    @Inject(method = "render", at = @At("HEAD"))
    public void renderSearchStuff(GuiGraphics drawContext, int mX, int mY, float delta, CallbackInfo ci) {
        if(showSearch && !config.hideSearchButton) {
            drawContext.fill(SEARCH_X - 2, height + SEARCH_Y_OFFSET - 2, (int) (width * (SEARCH_W_MULT + 0.06)), height + SEARCH_Y_OFFSET + SEARCH_H - 2, minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
            searchField.render(drawContext, mX, mY, delta);

            // renders a suggestion-esq error message if the regex search is invalid
            if(searchError != null) {
                int x = searchField.getX() + 8 + (int) (width * SEARCH_W_MULT);
                drawContext.drawCenteredString(font, searchError.getMessage().split( System.lineSeparator() )[0], x, searchField.getY(), 0xD00000);
            }
        }

        // renders the bg and the buttons for the settings menu
        if(showSettingsMenu && !config.hideSearchButton) {
            drawContext.blit(
                    ResourceLocation.tryBuild(ChatBoost.MOD_ID, "textures/gui/search_settings_panel.png"),
                    MENU_X,  height + MENU_Y_OFFSET, 0, 0, MENU_WIDTH, MENU_HEIGHT, MENU_WIDTH, MENU_HEIGHT
            );

            caseSensitive.button.render(drawContext, mX, mY, delta);
            modifiers.button.render(drawContext, mX, mY, delta);
            regex.button.render(drawContext, mX, mY, delta);
        }

        // renders the copy menu's selection box and menu buttons
        if( showCopyMenu && !hoveredVisibles.isEmpty() && !isMouseOverSettingsMenu(mX, mY) ) {
            ChatComponent chatHud = minecraft.gui.getChat();
            ChatHudAccessor chat = ChatHudAccessor.from(chatHud);
            List<GuiMessage.Line> visibles = chat.chatPatches$getVisibleMessages();


            int hoveredParts = hoveredVisibles.size();
            // ChatHud#render variables, most of which are based on the hovered message
            final double s = chatHud.getScale();
            final int lH = chat.chatPatches$getLineHeight();
            final int sW = Mth.ceil(chatHud.getWidth() / s); // scaled width
            final int sH = Mth.floor((minecraft.getWindow().getGuiScaledHeight() - 40) / s); // scaled height
            int shift = Mth.floor(config.shiftChat / s);
            int i = visibles.indexOf( hoveredVisibles.get(hoveredParts - 1) ) - chat.chatPatches$getScrolledLines();
            int hoveredY = sH - (i * lH) - shift;

            drawContext.pose().pushPose();
            drawContext.pose().scale((float) s, (float) s, 1.0f);

            int borderW = sW + 8;
            int scissorY1 = Mth.floor((sH - (chatHud.getLinesPerPage() * lH) - shift - 1) * s);
            int scissorY2 = Mth.floor((sH - shift + 1) * s);
            int selectionY1 = hoveredY - (lH * hoveredParts);
            int selectionH = (lH * hoveredParts) + 1;

            // cuts off any of the selection rect that goes past the chat hud
            drawContext.enableScissor(0, scissorY1, borderW, scissorY2);
            drawContext.renderOutline(0, selectionY1, borderW, selectionH, config.copyColor + 0xff000000);
            drawContext.disableScissor();

            drawContext.pose().popPose();


            mainButtons.values().forEach(menuButton -> menuButton.render(drawContext, mX, mY, delta));
            hoverButtons.values().forEach(menuButton -> {
                if(menuButton.is( COPY_LINK_N.apply(0) ))
                    mainButtons.get(COPY_MENU_LINKS).children.forEach(linkButton -> linkButton.render(drawContext, mX, mY, delta));
                else
                    menuButton.render(drawContext, mX, mY, delta);
            });
        }
    }


    /**
     * Only renders a tooltip if the mouse is not hovering over the (opened)
     * settings menu or the (shown) copy menu. In other words, returns
     * {@code true} if the mouse is NOT hovering over the <i>opened</i> settings
     * menu or the <i>shown</i> copy menu.
     * */
    @WrapWithCondition(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;renderComponentHoverEffect(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Style;II)V"))
    public boolean renderTooltipSmartly(GuiGraphics drawContext, Font hoverevent$entitytooltipinfo, Style hoverevent, int mX, int mY) {
        return !isMouseOverSettingsMenu(mX, mY) && !isMouseOverCopyMenu(mX, mY);
    }

    @Inject(method = "resize", at = @At("TAIL"))
    public void updateSearchOnResize(Minecraft minecraft, int i, int j, CallbackInfo ci) {
        String text = searchField.getValue();
        searchField.setValue(text);
        onSearchFieldUpdate(text);

        if(showCopyMenu)
            loadCopyMenu(anchor.x, anchor.y);

        if(!text.isEmpty())
            searchField.setSuggestion(null);
    }

    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;tick()V"))
    public void tickSearchField(EditBox chatField, Operation<Void> tick) {
        if(updateSearchColor)
            onSearchFieldUpdate(searchField.getValue());

        if(searchField.isFocused() && !config.hideSearchButton)
            searchField.tick();
        else
            tick.call(chatField);
    }

    /**
     * Either resets or saves the drafts for the search and chat fields, depending on
     * {@link Config#searchDrafting} and {@link Config#messageDrafting}.
     * Additionally, resets the chat hud.
     */
    @Inject(method = "removed", at = @At("TAIL"))
    public void onScreenClose(CallbackInfo ci) {
        if(config.messageDrafting)
            messageDraft = input.getValue();

        if(config.searchDrafting)
            searchDraft = searchField.getValue();
        else if(!searchField.getValue().isEmpty()) // reset the hud if it had anything in the field (helps fix #102)
            minecraft.gui.getChat().rescaleChat();

        resetCopyMenu();
    }

    /** Closes the settings menu if the escape key was pressed and it was already open, otherwise closes the screen. */
    @WrapOperation(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 0))
    public void allowClosingSettings(Minecraft client, Screen nullScreen, Operation<Void> setScreen) {
        if(showSettingsMenu)
            showSettingsMenu = false;
        else
            setScreen.call(client, null);
    }
    /** Clears the message draft **AFTER** a message has been (successfully) sent. Uses At.Shift.AFTER to ensure we don't clear if an error occurs */
    @Inject(method = "keyPressed", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;setScreen(Lnet/minecraft/client/gui/screens/Screen;)V", ordinal = 1, shift = At.Shift.AFTER))
    private void onMessageSentEmptyDraft(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        messageDraft = "";
    }

    /** Lets the {@link #chatField} widget be focused (?) when the search button isn't showing. (needs further testing) */
    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/EditBox;mouseClicked(DDI)Z"))
    private boolean disableChatFieldFocus(EditBox chatField, double mX, double mY, int button, Operation<Boolean> mouseClicked) {
        if(!config.hideSearchButton)
            return false;
        return mouseClicked.call(chatField, mX, mY, button);
    }
    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;handleChatQueueClicked(DD)Z"))
    private boolean fixMenuClickthroughClick(ChatComponent chatHud, double mX, double mY, Operation<Boolean> mouseClicked) {
        if(isMouseOverSettingsMenu(mX, mY) || isMouseOverCopyMenu(mX, mY))
            return false;
        else
            return mouseClicked.call(chatHud, mX, mY);
    }

    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/ChatScreen;getComponentStyleAt(DD)Lnet/minecraft/network/chat/Style;"))
    private Style fixMenuClickthroughStyle(ChatScreen screen, double mX, double mY, Operation<Style> getTextStyleAt) {
        if(isMouseOverSettingsMenu(mX, mY) || isMouseOverCopyMenu(mX, mY))
            return null;
        else
            return getTextStyleAt.call(screen, mX, mY);
    }
    /**
     * Returns {@code true} if the mouse clicked on any of the following:
     * <ul>
     * 		<li>If {@link #showSettingsMenu} is true, checks if these were clicked:</li>
     * 		<ul>
     * 			<li>{@link ChatSearchSetting#caseSensitive}</li>
     * 			<li>{@link ChatSearchSetting#modifiers}</li>
     * 			<li>{@link ChatSearchSetting#regex}</li>
     * 		</ul>
     * 		<li>Else if {@link #showSettingsMenu} is false:</li>
     * 		<ul>
     * 			<li>If the mouse right-clicked, tries to load the copy menu ({@link #loadCopyMenu(double, double)})</li>
     * 			<li>Otherwise if the mouse left-clicked and {@link #showCopyMenu} is true, checks if any menu buttons were clicked on</li>
     * 			<li>If nothing was clicked on, disables {@link #showCopyMenu}</li>
     * 		</ul>
     * </ul>
     */
    @Inject(method = "mouseClicked", at = @At("TAIL"), cancellable = true)
    public void afterClickBtn(double mX, double mY, int button, CallbackInfoReturnable<Boolean> cir) {
        if(cir.getReturnValue())
            return;

        if(searchField.mouseClicked(mX, mY, button))
            cir.setReturnValue(true);

        if(showSettingsMenu) {
            if(caseSensitive.button.mouseClicked(mX, mY, button))
                cir.setReturnValue(true);
            if(modifiers.button.mouseClicked(mX, mY, button))
                cir.setReturnValue(true);
            if(regex.button.mouseClicked(mX, mY, button))
                cir.setReturnValue(true);
        } else {
            // if button is a right-click, then try and render the copy menu
            if( button == GLFW.GLFW_MOUSE_BUTTON_RIGHT ) {
                RenderUtils.MousePos before = anchor;
                anchor = RenderUtils.MousePos.of((int) mX, (int) mY);
                if( loadCopyMenu(mX, mY) ) {
                    cir.setReturnValue(showCopyMenu = true);
                } else {
                    anchor = before;
                    cir.setReturnValue(showCopyMenu = false);
                }
            } else if( button == GLFW.GLFW_MOUSE_BUTTON_LEFT && showCopyMenu ) {
                // see if the mouse clicked on a menu button
                if( mainButtons.values().stream().anyMatch(menuButton -> menuButton.mouseClicked(mX, mY, button)) )
                    cir.setReturnValue(true);

                if( hoverButtons.values().stream().anyMatch(hoverButton -> hoverButton.mouseClicked(mX, mY, button)) )
                    cir.setReturnValue(true);
                else if( mainButtons.get(COPY_MENU_LINKS).children.stream().anyMatch(linkButton -> linkButton.mouseClicked(mX, mY, button)) )
                    cir.setReturnValue(true);

                // otherwise the mouse clicked off of the copy menu, so reset the selection
                cir.setReturnValue(showCopyMenu = false);
            }
        }
    }

    /** Allows hovering over certain menu buttons to reveal/hide nested buttons. */
    @Unique
    @Override
    public void mouseMoved(double mX, double mY) {
        if(showCopyMenu) {
            mainButtons.values().forEach(menuButton -> menuButton.mouseMoved(mX, mY));
            // as of right now, all hover menu buttons have copy actions and don't utilize #onMouseMoved
            //hoverButtons.values().forEach(menuButton -> menuButton.mouseMoved(mX, mY));
        }
    }

    // New/Unique methods

    @Unique
    private boolean isMouseOverSettingsMenu(double mX, double mY) {
        return showSettingsMenu && (mX >= MENU_X && mX <= MENU_X + MENU_WIDTH && mY >= height + MENU_Y_OFFSET && mY <= height + MENU_Y_OFFSET + MENU_HEIGHT);
    }

    @Unique
    private boolean isMouseOverCopyMenu(double mX, double mY) {
        return showCopyMenu && Stream.concat(mainButtons.values().stream(), hoverButtons.values().stream()).anyMatch(menuButton -> menuButton.isMouseOver(mX, mY));
    }


    /**
     * Returns the full chat message at the given coordinates, calculated using
     * the built-in {@link ChatHud#getMessageLineIndex(double, double)},
     * {@link ChatHud#toChatLineX(double)}, and {@link ChatHud#toChatLineY(double)} methods
     * combined with some logic to determine the entire message from only the hovered
     * (visible) message.
     * <padding>Automatically adjusts the parameters {@code mX} and {@code mY} to be accurate values,
     * including any shifts according to {@link Config#shiftChat}. If either {@code mX} or
     * {@code mY} are equal to {@code -1}, then an empty List is returned.</padding>
     *
     * @implNote
     * <ol>
     *	 <li>Gets the hovered visible index from {@link ChatHud#getMessageLineIndex(double, double)}
     *	 with {@link ChatHud#toChatLineX(double)} and {@link ChatHud#toChatLineY(double)} as parameters</li>
     *	 <li>If the hovered index is -1 then return an empty string</li>
     *	 <li>If the hovered message **IS** end of entry (EoE):</li>
     *	 <ol>
     *	     <li>Starting at the hovered index +1, iterates up (+) through the visible message list until reaching another EoE message</li>
     *	     <li>Reduces the start index by one (-1) to refer to the first message rather than the end of another message</li>
     *	     <li>Saves the end index as the hovered index because we know it's EoE</li>
     *	 </ol>
     *	 <li>Otherwise when the hovered message **IS NOT** EoE:</li>
     *	 <ol>
     *	     <li>Starting at the hovered index, iterates up (+) through the visible message list until reaching another EoE message</li>
     *	     <li>Reduces the start index by one (-1) to refer to the first message rather than the end of another message</li>
     *		 <li>Then starting at the start index, iterates down (-) through the visible message list until reaching our EoE message </li>
     *	 </ol>
     *	 <li>Iterates through the range just determined from start to the end index, concatenating all the message parts into one message</li>
     *	 <li>Returns the full message</li>
     * </ol>
     */
    @Unique
    private @NotNull List<GuiMessage.Line> getFullMessageAt(double mX, double mY) {
        if(mX < 0 || mY < 0)
            return new ArrayList<>(0);

        final ChatHudAccessor chatHud = ChatHudAccessor.from(minecraft);
        final List<GuiMessage.Line> visibles = chatHud.chatPatches$getVisibleMessages();
        // using LineIndex instead of Index bc during testing they both returned the same value; LineIndex has less code
        final int hoveredIndex = chatHud.chatPatches$getMessageLineIndex(chatHud.chatPatches$toChatLineX(mX), chatHud.chatPatches$toChatLineY(mY + config.shiftChat));

        if(hoveredIndex == -1)
            return new ArrayList<>(0);

        int start_index;
        int end_index;


        if(visibles.get(hoveredIndex).endOfEntry()) {
            start_index = hoveredIndex + 1; // w/o the +1, the loop would exit immediately
            while( start_index < visibles.size() && !visibles.get(start_index).endOfEntry() ) {
                start_index++;
            }
            start_index--; // now the start_index is actually the start and not the end of another message

            end_index = hoveredIndex; // end_index is the hovered index bc we know it's EoE
        } else {
            start_index = hoveredIndex;
            while( start_index < visibles.size() && !visibles.get(start_index).endOfEntry() ) {
                start_index++;
            }
            start_index--;

            end_index = start_index;
            while( end_index >= 0 && !visibles.get(end_index).endOfEntry() ) {
                end_index--;
            }
            // we don't need to add 1 to end_index bc it's already the end of the message
        }


        // note that the start_index is always greater than the end_index bc the newest message index = 0
        List<GuiMessage.Line> messageParts = new ArrayList<>(start_index - end_index);
        for(int i = start_index; i >= end_index; i--)
            messageParts.add( visibles.get(i) );

        return messageParts;
    }

    /**
     * @return {@code true} if the copy menu was loaded in any working capacity,
     * {@code false} otherwise. Notably returns {@code false} if the coordinates
     * are invalid, if no message is detected at the mouse pos, or if the respective
     * {@link ChatHudLine} from {@link #getFullMessageAt(double, double)} doesn't exist.
     *
     * @implNote The goal of this method is to do as much work as possible, so that
     * {@link #render(DrawContext, int, int, float)} does minimal work and maximum rendering.
     */
    @Unique
    private boolean loadCopyMenu(double mX, double mY) {
        if(mX < 0 || mY < 0)
            return false;
        resetCopyMenu();

        hoveredVisibles = getFullMessageAt(mX, mY);
        if(hoveredVisibles.isEmpty())
            return false;


        ChatComponent chatHud = minecraft.gui.getChat();
        ChatHudAccessor chat = ChatHudAccessor.from(chatHud);
        String hoveredMessageFirst = StringTextUtils.reorder( hoveredVisibles.get(0).content(), false );

        // warning: longer messages sometimes fail because extra spaces appear to be added,
        // so i switched it to a startsWith() bc the first one never has extra spaces. /!\ can probably still fail /!\
        // get hovered message index (messages) for all copying data
        selectedLine =
                chat.chatPatches$getMessages()
                        .stream()
                        .filter( msg -> ChatFormatting.stripFormatting( msg.content().getString() ).startsWith(hoveredMessageFirst) )
                        .findFirst()
                        .orElse(NIL_HUD_LINE);
        if(selectedLine.equals(NIL_HUD_LINE))
            return false;


        // decide which mainButtons should be rendering:
        // COPY_MENU_STRING is already positioned, so we just let it show
        mainButtons.get(COPY_MENU_STRING).button.visible = true;

        // add link button
        List<String> links = StringTextUtils.getLinks( selectedLine.content().getString() );
        if( !links.isEmpty() ) {
            for(String link : links) {
                MenuButtonWidget linkButton = of(1, COPY_LINK_N.apply( links.indexOf(link) + 1 ), () -> link);
                linkButton.xOffset = mainButtons.get(COPY_MENU_LINKS).width;
                linkButton.button.setTooltip(Tooltip.create(Component.nullToEmpty( "§9" + link )));
                mainButtons.get(COPY_MENU_LINKS).children.add(linkButton);
            }
            mainButtons.get(COPY_MENU_LINKS).readyToRender(true);
        }

        // add timestamp button
        if( !selectedLine.content().getSiblings().get(ChatUtils.TIMESTAMP_INDEX).getString().isBlank() )
            mainButtons.get(COPY_MENU_TIMESTAMP).readyToRender(true);
        mainButtons.get(COPY_UNIX).readyToRender(true);

        // add player data and reply buttons
        Component originalMessage = selectedLine.content().getSiblings().size() > ChatUtils.OG_MSG_INDEX ? selectedLine.content().getSiblings().get(ChatUtils.OG_MSG_INDEX) : Component.empty();
        Style style = originalMessage.getSiblings().size() > 0 ? originalMessage.getSiblings().get(ChatUtils.MSG_NAME_INDEX).getStyle() : Style.EMPTY;
        if( !style.equals(Style.EMPTY) && style.getHoverEvent() != null && style.getHoverEvent().getAction() == HoverEvent.Action.SHOW_ENTITY ) {
            PlayerInfo player = minecraft.getConnection().getPlayerInfo(UUID.fromString(hoverButtons.get(COPY_UUID).copySupplier.get()));
            // gets the skin texture from the player, then the profile, and finally the NIL profile if all else fails
            ResourceLocation skinTexture =
                    player == null
                            ? minecraft.getSkinManager().getInsecureSkinLocation( ChatUtils.NIL_MSG_DATA.sender() )
                            : player.isSkinLoaded()
                            ? player.getSkinLocation()
                            : minecraft.getSkinManager().getInsecureSkinLocation( player.getProfile() != null ? player.getProfile() : ChatUtils.NIL_MSG_DATA.sender() );

            mainButtons.get(COPY_MENU_SENDER).setTexture(skinTexture).readyToRender(true);
            mainButtons.get(COPY_MENU_REPLY).setTexture(skinTexture).readyToRender(true);
        }


        // decide which hoverButtons should be rendering:
        mainButtons.values().forEach(menuButton -> {
            // for most buttons, aligns them, offsets them, and updates their tooltip
            // for the link button, just aligns and offsets them
            if(menuButton.is(COPY_MENU_LINKS))
                for(int i = 0; i < menuButton.children.size();)
                    menuButton.children.get(i).alignTo(menuButton).offsetY(++i);
            else
                for(MenuButtonWidget child : menuButton.children)
                    child.alignTo(menuButton).offsetY(menuButton.children.indexOf(child) + 1).updateTooltip();
        });

        // get the most visible buttons from either menu and multiply those by the height of each button
        long mainHeight = mainButtons.values().stream().filter(menuButton -> menuButton.button.visible).count();
        long hoverHeight = hoverButtons.values().stream().filter(menuButton -> menuButton.button.visible).count();
        int copyMenuHeight = (int) Math.max(mainHeight, hoverHeight) * MenuButtonWidget.height;
        // if the menu goes off the screen, shift it up by the amount cut off + buffer just in case
        if( copyMenuHeight + anchor.y > minecraft.getWindow().getHeight() )
            anchor.y -= (copyMenuHeight - (minecraft.getWindow().getHeight() - anchor.y)) + 2;

        return showCopyMenu = true;
    }

    /**
     * Hides the copy menu, clears hovered visibles and the selected message,
     * resets button offsets, and cancels rendering for all buttons.
     */
    @Unique
    private void resetCopyMenu() {
        showCopyMenu = false;
        selectedLine = NIL_HUD_LINE;
        hoveredVisibles.clear();

        MenuButtonWidget.mainOffsets = 0;
        MenuButtonWidget.hoverOffsets = 0;

        mainButtons.values().forEach(MenuButtonWidget::cancelRender);
        hoverButtons.values().forEach(menuButton -> {
            if(menuButton.is( COPY_LINK_N.apply(0) ))
                mainButtons.get(COPY_MENU_LINKS).children.clear(); // clears the nLinkButtons list if the button is the placeholder
            else
                menuButton.cancelRender();
        });
    }

    /** Called when the search field is updated; also sets the regex error and the text input color. */
    @Unique
    private void onSearchFieldUpdate(String text) {
        // if text equals last search and shouldn't update the search color OR text is null, cancel
        if(text.equals( lastSearch != null ? lastSearch : "" ) && !updateSearchColor)
            return;

        if(!text.isEmpty()) {
            searchField.setSuggestion(null);

            // if regex is enabled and the text is invalid, set the error and color
            if(regex.on) {
                try {
                    Pattern.compile(text);
                    searchError = null;
                } catch(PatternSyntaxException e) {
                    searchError = e;
                    searchField.setTextColor(0xFF5555);
                    minecraft.gui.getChat().rescaleChat();
                }
            }

            List<GuiMessage.Line> searchResults = filterMessages( searchError != null ? null : text );
            if(searchError == null && searchResults.isEmpty()) { // mark the text yellow if there are no results
                searchField.setTextColor(0xFFFF55);
                minecraft.gui.getChat().rescaleChat();
            } else if(!searchResults.isEmpty()) { // mark the text green if there are results, and only show those
                searchField.setTextColor(0x55FF55);

                // todo: ensure that when this method is run, any successful matches are cached so subsequent searches only look through that list and are faster
                // might alr be done, idk
                ChatHudAccessor chatHud = ChatHudAccessor.from(minecraft);
                chatHud.chatPatches$getVisibleMessages().clear();
                chatHud.chatPatches$getVisibleMessages().addAll(searchResults);
            }
        } else {
            minecraft.gui.getChat().rescaleChat();

            searchError = null;
            searchField.setTextColor(0xE0E0E0); // default from TextFieldWidget
            searchField.setSuggestion(SUGGESTION);
        }

        lastSearch = text;
        updateSearchColor = false;
    }

    /**
     * Filters all {@link ChatHudLine} messages from the {@link #client}'s ChatHud
     * matching the target string (configuration applied from {@link ChatSearchSetting#caseSensitive},
     * {@link ChatSearchSetting#modifiers}, and {@link ChatSearchSetting#regex}) into a list of
     * {@link ChatHudLine.Visible} visibleMessages to be rendered onto the ChatHud. This does <u>not</u>
     * mutate or modify the actual {@link ChatHud#messages} list, only the {@link ChatHud#visibleMessages}
     * list that is automatically repopulated with new messages when needed.
     */
    @Unique
    private List<GuiMessage.Line> filterMessages(String target) {
        final ChatHudAccessor chatHud = ChatHudAccessor.from(minecraft);
        if(target == null)
            return createVisibles( chatHud.chatPatches$getMessages() );

//        List<GuiMessage> msgs = Lists.newArrayList( chatHud.chatPatches$getMessages() );
//        msgs.removeIf(hudLn -> {
//            String content = StringTextUtils.reorder(hudLn.content().getVisualOrderText(), modifiers.on);
//
//            // note that this NOTs the whole expression to simplify the complex nesting
//            // *removes* the message if it *doesn't* match
//            return !(
//                    regex.on
//                            ? content.matches( (caseSensitive.on ? "(?i)" : "") + target )
//                            : (
//                            caseSensitive.on
//                                    ? content.contains(target)
//                                    : StringUtils.containsIgnoreCase(content, target)
//                    )
//            );
//        });
        List<GuiMessage> msgs = new ArrayList<>();
        int pageSize = 100;
        int page = 1;

        int totalCount = ChatData.getInstance().messageCount();
        int totalPages = (int) Math.ceil((double) totalCount / pageSize);

        while (page <= totalPages) {
            int offset = (page - 1) * pageSize;

            List<ChatData.MessageSql> messages = ChatData.getInstance().query(offset, pageSize);

            messages.stream()
                    .map(t -> new GuiMessage(minecraft.gui.getGuiTicks(), t.message(), null, new GuiMessageTag(0x382fb5, null, null, "Restored")))
                    .filter(hudLn -> {
                        String content = StringTextUtils.reorder(hudLn.content().getVisualOrderText(), modifiers.on);

                        return (
                                regex.on
                                        ? content.matches((caseSensitive.on ? "(?i)" : "") + target)
                                        : (caseSensitive.on ? content.contains(target) : StringUtils.containsIgnoreCase(content, target))
                        );
                    })
                    .forEach(msgs::add);

            page++;
        }

//        List<GuiMessage> msgs = ChatData.getInstance().query().stream()
//                .map(t -> new GuiMessage(minecraft.gui.getGuiTicks(), t.message(), null, new GuiMessageTag(0x382fb5, null, null, "Restored")))
//                .filter(hudLn -> {
//                    String content = StringTextUtils.reorder(hudLn.content().getVisualOrderText(), modifiers.on);
//
//                    return (
//                            regex.on
//                                    ? content.matches((caseSensitive.on ? "(?i)" : "") + target)
//                                    : (caseSensitive.on ? content.contains(target) : StringUtils.containsIgnoreCase(content, target))
//                    );
//                })
//                .collect(Collectors.toList());
        Collections.reverse(msgs);

        return createVisibles(msgs);
    }

    /**
     * Creates a new list of to-be-rendered chat messages from the given list
     * of chat messages. The steps to achieving this are largely based on
     * the first half of the {@link ChatHud#addMessage(Text, MessageSignatureData, int, MessageIndicator, boolean)}
     * method, specifically everything before the {@code while} loop.
     */
    @Unique
    private List<GuiMessage.Line> createVisibles(List<GuiMessage> messages) {
        List<GuiMessage.Line> generated = Lists.newArrayListWithExpectedSize(messages.size());
        ChatComponent chatHud = minecraft.gui.getChat();

        messages.forEach(hudLn -> {
            GuiMessageTag ind = hudLn.tag();
            int width = (int) ((double)chatHud.getWidth() / chatHud.getScale()) - (ind != null && ind.icon() != null ? ind.icon().width + 6 : 0);
            List<FormattedCharSequence> list = ComponentRenderUtils.wrapComponents(hudLn.content(), width, this.minecraft.font);

            for(int i = list.size()-1; i >= 0; --i)
                generated.add(new GuiMessage.Line(hudLn.addedTime(), list.get(i), ind, (i == list.size() - 1)));
        });

        return generated;
    }
}
