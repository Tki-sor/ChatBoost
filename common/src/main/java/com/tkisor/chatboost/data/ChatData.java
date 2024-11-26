package com.tkisor.chatboost.data;

import com.tkisor.chatboost.ChatBoost;
import com.tkisor.chatboost.util.Flags;
import dev.architectury.platform.Platform;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Date;

public class ChatData {
    private static volatile ChatData instance;
    private final Path dbPath;
    private Connection connection;

    private ChatData(Path dbPath) {
        this.dbPath = dbPath;
    }

    public static String formatTime() {
        LocalDateTime dateTime = LocalDateTime.ofInstant(new Date().toInstant(), ZoneId.systemDefault());
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        return dateTime.format(formatter);
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
                initialize();
            }
        } catch (SQLException ignored) {

        }

        String type = ChatBoost.gameType;
        String name = ChatBoost.gameName;

        try {
            insert(type, name, message, timestamp);
        } catch (Exception e) {
            ChatBoost.Logger.error("ChatBoost SQL Error:", e);
        }
    }

    private void insert(String type, String name, String message, String timestamp) throws SQLException {
        String sql = "INSERT INTO ChatLogs (type, name, message, timestamp) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, type);
            pstmt.setString(2, name);
            pstmt.setString(3, message);
            pstmt.setString(4, timestamp);

            pstmt.executeUpdate();
        }
    }

    public void delete(String type, String name) {
        String query = "DELETE FROM ChatLogs WHERE type = ? AND name = ?";

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, type);
            stmt.setString(2, name);

            int affectedRows = stmt.executeUpdate();
            if (affectedRows > 0) {
                System.out.println("Successfully deleted " + affectedRows + " rows.");
            } else {
                System.out.println("No matching rows found to delete.");
            }
        } catch (SQLException e) {
            throw new RuntimeException("Error deleting messages by type and name", e);
        }
    }


    public static void restore(Minecraft client) {
        Flags.LOADING_CHATLOG.raise();

        String type = ChatBoost.gameType;
        String name = ChatBoost.gameName;
        getInstance().query(type, name).forEach(msg -> {
            client.gui.getChat().addMessage(msg.message(), null, new GuiMessageTag(0x382fb5, null, null, "Restored"));

        });

        Flags.LOADING_CHATLOG.lower();
    }

    public List<MessageSql> query(String type, String name) {
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }
            String sql = "SELECT id, type, name, message, timestamp FROM ChatLogs WHERE type = ? AND name = ?";
            List<MessageSql> results = new ArrayList<>();

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, type);
                pstmt.setString(2, name);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Component message = ChatBoost.json.fromJson(rs.getString("message"), Component.class);
                        results.add(new MessageSql(rs.getString("type"), rs.getString("name"), message, rs.getString("timestamp")));
                    }
                }
            }
            return results;
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public List<MessageSql> findMessagesAround(String message, String direction, int count) {
        List<MessageSql> result = new ArrayList<>();
        String query;

        if ("forward".equalsIgnoreCase(direction)) {
            query = "SELECT type, name, message, timestamp FROM ChatLogs WHERE message >= ? ORDER BY timestamp ASC LIMIT ? OFFSET ?";
        } else if ("backward".equalsIgnoreCase(direction)) {
            query = "SELECT type, name, message, timestamp FROM ChatLogs WHERE message <= ? ORDER BY timestamp DESC LIMIT ? OFFSET ?";
        } else {
            throw new IllegalArgumentException("Direction must be 'forward' or 'backward'");
        }

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, message);
            stmt.setInt(2, count + 1);
            stmt.setInt(3, 0);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String type = rs.getString("type");
                    String name = rs.getString("name");
                    String messageJson = rs.getString("message");
                    String timestamp = rs.getString("timestamp");

                    Component messageComponent = ChatBoost.json.fromJson(messageJson, Component.class);
                    result.add(new MessageSql(type, name, messageComponent, timestamp));
                }
            }

            if (result.size() > count) {
                if ("forward".equalsIgnoreCase(direction)) {
                    result = result.subList(1, count + 1);
                } else {
                    result = result.subList(result.size() - count, result.size());
                }
            }
        } catch (SQLException e) {
            ChatBoost.Logger.error("[ChatData.findMessagesAround]Find message Error: ", e);
        }

        if (result.size()==1) {
            if (ChatBoost.json.toJson(result.get(0).message, Component.class).equals(message)) {
                return new ArrayList<>();
            }
        }

        return result;
    }

    public record MessageSql(String type, String name, Component message, String time) {}

    public void close() {
        if (connection != null) {
            try {
                connection.close();
                instance = null;
            } catch (SQLException e) {
                ChatBoost.Logger.error(e.getMessage());
            }
        }
    }
}
