package com.tkisor.chatboost.data;


import com.mojang.authlib.minecraft.client.MinecraftClient;
import com.tkisor.chatboost.ChatBoost;
import com.tkisor.chatboost.util.Flags;
import dev.architectury.platform.Platform;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatLog;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class ChatData {
    private static volatile ChatData instance;
    private final Path dbPath;
    private Connection connection;

    private ChatData(Path dbPath) {
        this.dbPath = dbPath;
    }

    public static ChatData getInstance() {
        if (instance == null) {
            synchronized (ChatData.class) {
                if (instance == null) {
                    Path gameFolder = Platform.getGameFolder();
                    Path dbPath = gameFolder.resolve("chatboost.db");
                    instance = new ChatData(dbPath);
                    try {
                        instance.initialize();
                    } catch (SQLException e) {
                        ChatBoost.Logger.error("ChatBoost SQL Init Error: ", e);
                    }
                }
            }
        }
        return instance;
    }

    private void initialize() throws SQLException {
        if (connection == null || connection.isClosed()) {
            String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                String createTableSQL = """
                CREATE TABLE IF NOT EXISTS ChatLogs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    type TEXT NOT NULL,        -- 来源类型：client 或 server
                    name TEXT NOT NULL,        -- 存档名称或服务器 IP
                    message TEXT NOT NULL,     -- 聊天信息（JSON 格式）
                    timestamp DATETIME NOT NULL, -- 消息时间戳
                    CHECK (type IN ('client', 'server')) -- 限制来源类型
                );
            """;

                stmt.execute(createTableSQL);
                String createTypeNameIndexSQL = "CREATE INDEX IF NOT EXISTS idx_type_name ON ChatLogs(type, name);";
                stmt.execute(createTypeNameIndexSQL);

            }
        }
    }

    public void insert(String message, String timestamp) {
        try {
            if (connection == null || connection.isClosed()) {
                initialize();  // 如果连接为 null 或已关闭，重新初始化
            }
        } catch (SQLException ignored) {

        }

        String type = ChatBoost.gameType;
        String name = ChatBoost.gameName;

        try {
            insert(type, name, message, timestamp);
        } catch (SQLException e) {
            ChatBoost.Logger.error("ChatBoost SQL Error:", e);
//            throw new RuntimeException(e);
        }
    }

    private void insert(String type, String name, String message, String timestamp) throws SQLException {
        String sql = "INSERT INTO ChatLogs (type, name, message, timestamp) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, type);
            pstmt.setString(2, name);
            pstmt.setString(3, message);
            pstmt.setString(4, timestamp); // 确保 timestamp 格式正确，如 '2024-11-01 10:00:00'

            pstmt.executeUpdate();
        }
    }

    public static void restore(Minecraft client) {
        Flags.LOADING_CHATLOG.raise();

        String type = ChatBoost.gameType;
        String name = ChatBoost.gameName;
        getInstance().query(type, name).forEach(msg -> {
            client.gui.getChat().addMessage(msg, null, new GuiMessageTag(0x382fb5, null, null, "Restored"));
        });

        Flags.LOADING_CHATLOG.lower();
    }

    public List<Component> query(String type, String name) {
        // 确保连接有效
        try {
            if (connection == null || connection.isClosed()) {
                initialize();  // 如果连接无效，重新初始化
            }
            String sql = "SELECT id, type, name, message, timestamp FROM ChatLogs WHERE type = ? AND name = ?";
            List<Component> results = new ArrayList<>();

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, type);
                pstmt.setString(2, name);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Component message = ChatBoost.json.fromJson(rs.getString("message"), Component.class);
                        results.add(message);
                    }
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    // 查询聊天记录
    public List<Component> query(String type, String name, String startTime, String endTime) throws SQLException {
        // 确保连接有效
        try {
            if (connection == null || connection.isClosed()) {
                initialize();  // 如果连接无效，重新初始化
            }
        } catch (SQLException ignored) {

        }

        String sql = "SELECT id, type, name, message, timestamp FROM ChatLogs WHERE type = ? AND name = ? AND timestamp BETWEEN ? AND ?";
        List<Component> results = new ArrayList<>();

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, type);
            pstmt.setString(2, name);
            pstmt.setString(3, startTime);  // 格式如 '2024-11-01 10:00:00'
            pstmt.setString(4, endTime);    // 格式如 '2024-11-01 12:00:00'

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
//                    ChatLog log = new ChatLog(
//                            rs.getInt("id"),
//                            rs.getString("type"),
//                            rs.getString("name"),
//                            rs.getString("message"),
//                            rs.getString("timestamp")
//                    );
                    Component message = ChatBoost.json.fromJson(rs.getString("message"), Component.class);
                    results.add(message);
                }
            }
        }
        return results;
    }

    public void close() {
        if (connection != null) {
            try {
                connection.close(); // 关闭数据库连接
                instance = null;
            } catch (SQLException e) {
                ChatBoost.Logger.error(e.getMessage());
            }
        }
    }
}
