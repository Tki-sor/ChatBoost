package com.tkisor.chatboost;

import com.google.gson.Gson;
import com.google.gson.InstanceCreator;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonSerializer;
import com.tkisor.chatboost.config.Config;
import com.tkisor.chatboost.data.ChatData;
import dev.architectury.event.events.client.ClientLifecycleEvent;
import dev.architectury.event.events.client.ClientPlayerEvent;
import dev.architectury.platform.Platform;
import net.fabricmc.api.EnvType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;

public final class ChatBoost {
    public static final org.slf4j.Logger Logger = org.slf4j.LoggerFactory.getLogger("Chat Boost");
    public static final String MOD_ID = "chatboost";

    public static Config config = Config.newConfig(false);
    public static String gameType = "";
    public static String gameName = "";

    public static final Gson json = new com.google.gson.GsonBuilder()
            .registerTypeAdapter(Component.class, (JsonSerializer<Component>) (src, type, context) -> Component.Serializer.toJsonTree(src))
            .registerTypeAdapter(Component.class, (JsonDeserializer<Component>) (json, type, context) -> Component.Serializer.fromJson(json))
            .registerTypeAdapter(Component.class, (InstanceCreator<Component>) type -> Component.empty())
            .create();


    public static void init() {
        // Write common init code here.


//        ClientChatEvent.RECEIVED.register((type, message) -> {
//
//            System.out.println("chat1: "+type.chatType().chat());
//            System.out.println("chat2: "+Component.Serializer.toJson(message));
//            return CompoundEventResult.pass();
//
//        });

        ChatData.getInstance();


        if (Platform.getEnv() == EnvType.CLIENT) {
            ClientLifecycleEvent.CLIENT_STOPPING.register(instance -> {
                ChatData.getInstance().close();
            });

            ClientPlayerEvent.CLIENT_PLAYER_JOIN.register(player -> {
                Minecraft client = Minecraft.getInstance();
                ServerData currentServer = client.getCurrentServer();
                if (client.hasSingleplayerServer()) {
                    gameType = "client";
                    gameName = client.getSingleplayerServer().getWorldData().getLevelName();
                } else {
                    gameType = "server";
                    gameName = currentServer.ip;
                }

                ChatData.restore(client);
            });
        }


    }

}
