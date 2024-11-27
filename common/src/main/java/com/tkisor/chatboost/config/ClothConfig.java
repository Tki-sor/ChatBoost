package com.tkisor.chatboost.config;

import com.tkisor.chatboost.ChatBoost;
import me.shedaniel.clothconfig2.api.*;
import me.shedaniel.clothconfig2.gui.entries.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;

public class ClothConfig {
    private Screen parent;

    private ClothConfig(Screen parent) {
        this.parent = parent;
    }

    public static Screen create(Screen parent) {
        return new ClothConfig(parent).builder();
    }


    private Screen builder() {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("text.chatpatches.title"));

        ConfigEntryBuilder entryBuilder = builder.entryBuilder();
        Config config = ChatBoost.config;

        // 时间戳
        ConfigCategory time = builder.getOrCreateCategory(Component.translatable("text.chatpatches.category.time"));
        BooleanListEntry time1 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.time"), config.time)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("text.chatpatches.desc.time"))
                .setSaveConsumer(save -> config.time = save)
                .build();
        DropdownBoxEntry<String> time2 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.timeDate"), config.timeDate)
                .setDefaultValue("HH:mm:ss")
                .setTooltip(Component.translatable("text.chatpatches.desc.timeDate"))
                .setSaveConsumer(save -> config.timeDate = save)
                .build();
        DropdownBoxEntry<String> time3 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.timeFormat"), config.timeFormat)
                .setDefaultValue("[$]")
                .setTooltip(Component.translatable("text.chatpatches.desc.timeFormat"))
                .setSaveConsumer(save -> config.timeFormat = save)
                .build();
        ColorEntry time4 = entryBuilder.startColorField(Component.translatable("text.chatpatches.timeColor"), config.timeColor)
                .setDefaultValue(0xff55ff)
                .setTooltip(Component.translatable("text.chatpatches.desc.timeColor"))
                .setSaveConsumer(save -> config.timeColor = save)
                .build();
        time.addEntry(time1)
                .addEntry(time2)
                .addEntry(time3)
                .addEntry(time4);

        // 悬停信息
        ConfigCategory hover = builder.getOrCreateCategory(Component.translatable("text.chatpatches.category.hover"));
        BooleanListEntry hover1 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.hover"), config.hover)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("text.chatpatches.desc.hover"))
                .setSaveConsumer(save -> config.hover = save)
                .build();
        DropdownBoxEntry<String> hover2 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.hoverDate"), config.hoverDate)
                .setDefaultValue("MM/dd/yyyy")
                .setTooltip(Component.translatable("text.chatpatches.desc.hoverDate"))
                .setSaveConsumer(save -> config.hoverDate = save)
                .build();
        DropdownBoxEntry<String> hover3 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.hoverFormat"), config.hoverFormat)
                .setDefaultValue("$")
                .setTooltip(Component.translatable("text.chatpatches.desc.hoverFormat"))
                .setSaveConsumer(save -> config.hoverFormat = save)
                .build();
        ColorEntry hover4 = entryBuilder.startColorField(Component.translatable("text.chatpatches.hoverColor"), config.hoverColor)
                .setDefaultValue(0xffffff)
                .setTooltip(Component.translatable("text.chatpatches.desc.hoverColor"))
                .setSaveConsumer(save -> config.hoverColor = save)
                .build();
        hover.addEntry(hover1)
                .addEntry(hover2)
                .addEntry(hover3)
                .addEntry(hover4);

        // 重复计数
        ConfigCategory counter = builder.getOrCreateCategory(Component.translatable("text.chatpatches.category.counter"));
        BooleanListEntry counter1 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.counter"), config.counter)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("text.chatpatches.desc.counter"))
                .setSaveConsumer(save -> config.counter = save)
                .build();
        DropdownBoxEntry<String> counter2 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.counterFormat"), config.counterFormat)
                .setDefaultValue("&8(&7x&r$&8)")
                .setTooltip(Component.translatable("text.chatpatches.desc.counterFormat"))
                .setSaveConsumer(save -> config.counterFormat = save)
                .build();
        ColorEntry counter3 = entryBuilder.startColorField(Component.translatable("text.chatpatches.counterColor"), config.counterColor)
                .setDefaultValue(0xffff55)
                .setTooltip(Component.translatable("text.chatpatches.desc.counterColor"))
                .setSaveConsumer(save -> config.counterColor = save)
                .build();
        counter.addEntry(counter1)
                .addEntry(counter2)
                .addEntry(counter3);

        // 会话边界
        ConfigCategory boundary = builder.getOrCreateCategory(Component.translatable("text.chatpatches.category.boundary"));
        BooleanListEntry boundary1 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.boundary"), config.boundary)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("text.chatpatches.desc.boundary"))
                .setSaveConsumer(save -> config.boundary = save)
                .build();
        DropdownBoxEntry<String> boundary2 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.boundaryFormat"), config.boundaryFormat)
                .setDefaultValue("&8[&r$&8]")
                .setTooltip(Component.translatable("text.chatpatches.desc.boundaryFormat"))
                .setSaveConsumer(save -> config.boundaryFormat = save)
                .build();
        ColorEntry boundary3 = entryBuilder.startColorField(Component.translatable("text.chatpatches.boundaryColor"), config.boundaryColor)
                .setDefaultValue(0xffff55)
                .setTooltip(Component.translatable("text.chatpatches.desc.boundaryColor"))
                .setSaveConsumer(save -> config.boundaryColor = save)
                .build();
        boundary.addEntry(boundary1)
                .addEntry(boundary2)
                .addEntry(boundary3);

        // 聊天界面
        ConfigCategory chat = builder.getOrCreateCategory(Component.translatable("text.chatpatches.category.chat"));
        BooleanListEntry hud1 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.chatLog"), config.chatLog)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("text.chatpatches.desc.chatLog"))
                .setSaveConsumer(save -> config.chatLog = save)
                .build();
        IntegerListEntry hud2 = entryBuilder.startIntField(Component.translatable("text.chatpatches.chatWidth"), config.chatWidth)
                .setDefaultValue(0)
                .setMin(0)
                .setMax(630)
                .setTooltip(Component.translatable("text.chatpatches.desc.chatWidth"))
                .setSaveConsumer(save -> config.chatWidth = save)
                .build();
        DropdownBoxEntry<String> hud4 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.chatNameFormat"), config.chatNameFormat)
                .setDefaultValue("<$>")
                .setTooltip(Component.translatable("text.chatpatches.desc.chatNameFormat"))
                .setSaveConsumer(save -> config.chatNameFormat = save)
                .build();
        List<AbstractConfigListEntry> huds = List.of(hud1, hud2, hud4);

        IntegerListEntry c1 = entryBuilder.startIntField(Component.translatable("text.chatpatches.shiftChat"), config.shiftChat)
                .setDefaultValue(10)
                .setMin(0)
                .setMax(100)
                .setTooltip(Component.translatable("text.chatpatches.desc.shiftChat"))
                .setSaveConsumer(save -> config.shiftChat = save)
                .build();

        BooleanListEntry c2 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.messageDrafting"), config.messageDrafting)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("text.chatpatches.desc.messageDrafting"))
                .setSaveConsumer(save -> config.messageDrafting = save)
                .build();
        BooleanListEntry c3 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.searchDrafting"), config.searchDrafting)
                .setDefaultValue(true)
                .setTooltip(Component.translatable("text.chatpatches.desc.searchDrafting"))
                .setSaveConsumer(save -> config.searchDrafting = save)
                .build();
        BooleanListEntry c4 = entryBuilder.startBooleanToggle(Component.translatable("text.chatpatches.hideSearchButton"), config.hideSearchButton)
                .setDefaultValue(false)
                .setTooltip(Component.translatable("text.chatpatches.desc.hideSearchButton"))
                .setSaveConsumer(save -> config.hideSearchButton = save)
                .build();

        List<AbstractConfigListEntry> screen = List.of(c1, c2, c3, c4);
        SubCategoryListEntry hudL = entryBuilder.startSubCategory(Component.translatable("text.chatpatches.category.chat.hud"), huds).build();
        SubCategoryListEntry screenL = entryBuilder.startSubCategory(Component.translatable("text.chatpatches.category.chat.screen"), screen).build();
        chat.addEntry(hudL);
        chat.addEntry(screenL);

        // 复制菜单
        ConfigCategory copy = builder.getOrCreateCategory(Component.translatable("text.chatpatches.category.copy"));
        ColorEntry copy1 = entryBuilder.startColorField(Component.translatable("text.chatpatches.copyColor"), config.copyColor)
                .setDefaultValue(0x55ffff)
                .setTooltip(Component.translatable("text.chatpatches.desc.copyColor"))
                .setSaveConsumer(save -> config.copyColor = save)
                .build();
        DropdownBoxEntry<String> copy2 = entryBuilder.startStringDropdownMenu(Component.translatable("text.chatpatches.copyReplyFormat"), config.copyReplyFormat)
                .setDefaultValue("/msg $ ")
                .setTooltip(Component.translatable("text.chatpatches.desc.copyReplyFormat"))
                .setSaveConsumer(save -> config.copyReplyFormat = save)
                .build();
        copy.addEntry(copy1)
                .addEntry(copy2);

        builder.setSavingRunnable(Config::write);
        return builder.build();
    }


}
