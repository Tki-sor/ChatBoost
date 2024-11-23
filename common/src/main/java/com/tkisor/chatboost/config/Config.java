package com.tkisor.chatboost.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.mojang.authlib.GameProfile;
import com.tkisor.chatboost.ChatBoost;
import dev.architectury.platform.Platform;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.*;
import net.minecraft.world.entity.EntityType;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static com.tkisor.chatboost.ChatBoost.Logger;
import static com.tkisor.chatboost.ChatBoost.config;
import static com.tkisor.chatboost.util.StringTextUtils.fillVars;
import static com.tkisor.chatboost.util.StringTextUtils.toText;
import static java.io.File.separator;

public class Config {
    public static final String CONFIG_PATH = Platform.getConfigFolder() + separator + "chatpatches.json";
    private static final Config DEFAULTS = new Config();

    // categories: time, hover, counter, counter.compact, boundary, chat.hud, chat.screen, copy
    public boolean time = true; public String timeDate = "HH:mm:ss"; public String timeFormat = "[$]"; public int timeColor = 0xff55ff;
    public boolean hover = true; public String hoverDate = "MM/dd/yyyy"; public String hoverFormat = "$"; public int hoverColor = 0xffffff;
    public boolean counter = true; public String counterFormat = "&8(&7x&r$&8)"; public int counterColor = 0xffff55;
    public boolean counterCompact = false; public int counterCompactDistance = 0;
    public boolean boundary = true; public String boundaryFormat = "&8[&r$&8]"; public int boundaryColor = 0x55ffff;
    public boolean chatLog = true, chatHidePacket = true; public int chatWidth = 0, chatMaxMessages = 16384; public String chatNameFormat = "<$>";
    public int shiftChat = 10; public boolean messageDrafting = false, searchDrafting = true, hideSearchButton = false, vanillaClearing = false;
    public int copyColor = 0x55ffff; public String copyReplyFormat = "/msg $ ";


    /** Creates a new Config or YACLConfig depending on installed mods. */
    public static Config newConfig(boolean reset) {
        boolean accessibleInGame = Platform.isForge() || Platform.isModLoaded("modmenu") || (Platform.isModLoaded("catalogue") && Platform.isModLoaded("menulogue"));
        config = accessibleInGame ? new Config() : DEFAULTS;

        if(!reset)
            read();
        write();

        return config;
    }

    public Screen getConfigScreen(Screen parent) {
        return null;
    }


    /** Returns all Config options as a List of string keys and class types that can be used with {@link #getOption(String)}. */
    public static List<ConfigOption<?>> getOptions() {
        List<ConfigOption<?>> options = new ArrayList<>( Config.class.getDeclaredFields().length );

        for(Field field : Config.class.getDeclaredFields()) {
            if(Modifier.isStatic( field.getModifiers() ))
                continue;

            options.add( Config.getOption(field.getName()) );
        }

        return options;
    }

    /** Returns the value of the option in the {@link ChatBoost#config} identified by {@code key}. */
    @SuppressWarnings("unchecked")
    public static <T> ConfigOption<T> getOption(String key) {
        try {
            return new ConfigOption<>( (T)config.getClass().getField(key).get(config), (T)Config.class.getField(key).get(DEFAULTS), key );

        } catch (IllegalAccessException | NoSuchFieldException e) {
            Logger.error("[Config.getOption({})] An error occurred while trying to get an option value, please report this on GitHub:", key, e);

            return new ConfigOption<>( (T)new Object(), (T)new Object(), key );
        }
    }


    /**
     * Creates a timestamp in a Text object using the specified time.
     * Uses the {@link #timeFormat}, {@link #timeDate}, and {@link #timeColor}
     * config options. Note that this still creates a timestamp even if
     * {@link #time} is false.
     */
    public MutableComponent makeTimestamp(Date when) {
        return (
            toText( fillVars(timeFormat, new SimpleDateFormat(timeDate).format(when)) + " " )
                .withStyle( Style.EMPTY.withColor(timeColor) )
        );
    }

    /**
     * Creates a text Style that contains extra timestamp information
     * when hovered over in-game. Uses {@link #hoverFormat}, {@link #hoverDate},
     * and {@link #hoverColor} to format the tooltip text. If {@link #hover} is
     * false, this will return a Style with only {@link #timeColor} used.
     */
    public Style makeHoverStyle(Date when) {
        final Style EMPTY = Style.EMPTY.withBold(false).withItalic(false).withUnderlined(false).withObfuscated(false).withStrikethrough(false);
        MutableComponent hoverText = toText( fillVars(hoverFormat, new SimpleDateFormat(hoverDate).format(when)) ).withStyle( EMPTY.withColor(hoverColor) );

        return EMPTY
            .withHoverEvent( hover ? new HoverEvent(HoverEvent.Action.SHOW_TEXT, hoverText) : null )
            .withClickEvent( hover ? new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, hoverText.getString()) : null )
            .withInsertion(String.valueOf( when.getTime() ))
            .withColor(timeColor)
        ;
    }

    public MutableComponent formatPlayername(GameProfile player) {
        String name = player.getName();
        return toText( fillVars(chatNameFormat, name) + " " )
            .setStyle( Style.EMPTY
                .withHoverEvent(
                    new HoverEvent(
                        HoverEvent.Action.SHOW_ENTITY,
                        new HoverEvent.EntityTooltipInfo( EntityType.PLAYER, player.getId(), Component.nullToEmpty(player.getName()) )
                    )
                )
                .withClickEvent( new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/tell " + name + " ") )
            );
    }

    public MutableComponent makeDupeCounter(int dupes) {
        return
            toText(" " + fillVars(counterFormat, Integer.toString(dupes)))
                .withStyle( Style.EMPTY.withColor(counterColor) );
    }

    public Component makeBoundaryLine(String levelName) {
        // constructs w empty texts to not throw errors when comparing for the dupe counter
        return Component.empty()
            .append(toText( fillVars(boundaryFormat, levelName) ).withStyle( Style.EMPTY.withColor(boundaryColor) ))
            .append(Component.empty());
    }


    /** Loads the config settings saved at {@link Config#CONFIG_PATH} into this Config instance */
    public static void read() {

        if( !Files.exists(Path.of(CONFIG_PATH)) )
            config = newConfig(true);

        else
            try(FileReader fr = new FileReader(CONFIG_PATH)) {
                config = new Gson().fromJson(fr, config.getClass());

                Logger.info("[Config.read] Loaded config info from '{}'!", CONFIG_PATH);
            } catch(JsonIOException | JsonSyntaxException e) {

                Logger.info("[Config.read] The config couldn't be loaded; copying old data and resetting...");
                writeCopy();
                reset();

            } catch(IOException e) {
                reset();
                Logger.error("[Config.read] An error occurred while trying to load config data from '{}':", CONFIG_PATH, e);
            }
    }

    /** Saves the {@code ChatPatches.config} instance to {@link Config#CONFIG_PATH} */
    public static void write() {
        try(FileWriter fw = new FileWriter(CONFIG_PATH)) {

            new GsonBuilder()
                .excludeFieldsWithModifiers(Modifier.STATIC)
                .setPrettyPrinting()
            .create()
                .toJson(config, config.getClass(), fw);

            Logger.info("[Config.write] Saved config info to '{}'!", CONFIG_PATH);
        } catch(Exception e) {
            Logger.error("[Config.write] An error occurred while trying to save config data to '{}':", CONFIG_PATH, e);
        }
    }

    /** Overwrites the {@code ChatPatches.config} object with default values and saves it */
    public static void reset() {
        config = Config.newConfig(true);
    }

    /** Copies the current Config file data to {@code ./chatpatches_old.json} for copying old configurations over */
    public static void writeCopy() {
        try(
            FileInputStream cfg = new FileInputStream(CONFIG_PATH);
            FileOutputStream copy = new FileOutputStream(CONFIG_PATH.replace("chatpatches", "chatpatches_old"))
        ) {
            copy.write( cfg.readAllBytes() );
        } catch (IOException e) {
            Logger.error("[Config.writeCopy] An error occurred trying to copy the original config file from '{}':", CONFIG_PATH, e);
        }
    }

    /**
     * A simple Option class that wraps the internally-used
     * String/Class pair for each Config field. This is
     * merely an abstraction used for simplification.
     */
    public static class ConfigOption<T> {
        private T val;
        public final T def;
        public final String key;

        /**
         * Creates a new Simple Config option.
         * @param def The default value for creation and resetting.
         * @param key The lang key of the Option; for identification
         */
        public ConfigOption(T val, T def, String key) {
            this.val = Objects.requireNonNull(val, "Cannot create a ConfigOption without a default value");
            this.def = Objects.requireNonNull(def, "Cannot create a ConfigOption without a default value");
            this.key = Objects.requireNonNull(key, "Cannot create a ConfigOption without a key");
        }


        public T get() { return val; }

        @SuppressWarnings("unchecked")
        public Class<T> getType() { return (Class<T>) def.getClass(); }

        /**
         * Sets this Option's value to {@code obj} in {@code this} and also in the config;
         * assuming {@code obj.getClass().equals(T.class)} returns true.
         * @param obj The new object to replace the old one with
         * @param set If false, doesn't change the value. For no check, see
         * {@link #set(Object)}
         */
        public void set(Object obj, boolean set) {
            try {
                @SuppressWarnings("unchecked")
                T inc = (T) obj;

                if( inc != null && !inc.equals(val) && set ) {
                    config.getClass().getField(key).set(config, inc);

                    this.val = inc;
                }
            } catch (NoSuchFieldException | IllegalAccessException | ClassCastException e) {
                Logger.error("[ConfigOption.set({})] An error occurred trying to set a config option:", obj, e);
            }
        }

        public void set(Object obj) {
            this.set(obj, true);
        }

        public boolean changed() {
            return !val.equals(def);
        }
    }
}