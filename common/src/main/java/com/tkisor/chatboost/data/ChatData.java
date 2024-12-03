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
            ChatBoost.Logger.error("Error deleting messages by type and name", e);
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

    public List<MessageSql> query(int offset, int limit) {
        String type = ChatBoost.gameType;
        String name = ChatBoost.gameName;
        try {
            if (connection == null || connection.isClosed()) {
                initialize();
            }

            String sql = "SELECT id, type, name, message, timestamp FROM ChatLogs WHERE type = ? AND name = ? LIMIT ? OFFSET ?";

            List<MessageSql> results = new ArrayList<>();

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, type);
                pstmt.setString(2, name);
                pstmt.setInt(3, limit);
                pstmt.setInt(4, offset);

                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        Component message = ChatBoost.json.fromJson(rs.getString("message"), Component.class);
                        results.add(new MessageSql(rs.getString("type"), rs.getString("name"), message, rs.getString("timestamp")));
                    }
                }
            }
            return results;
        } catch (SQLException e) {
            return new ArrayList<>();
        }
    }


    public List<MessageSql> query() {
        String type = ChatBoost.gameType;
        String name = ChatBoost.gameName;
        return query(type, name);
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
            return new ArrayList<>();
//            throw new RuntimeException(e);
        }
    }

    public List<MessageSql> findMessages(String message, String direction, int count) {
        List<MessageSql> result = new ArrayList<>();
        String time = queryTime(message);
        String query;

        if ("forward".equalsIgnoreCase(direction)) {
            query = "SELECT type, name, message, timestamp FROM ChatLogs WHERE type = ? AND name = ? AND timestamp >= ? ORDER BY timestamp ASC limit ? offset 1";
        } else if ("backward".equalsIgnoreCase(direction)) {
            query = "SELECT type, name, message, timestamp FROM ChatLogs WHERE type = ? AND name = ? AND timestamp <= ? ORDER BY timestamp DESC limit ? offset 1";
        } else {
            throw new IllegalArgumentException("Direction must be 'forward' or 'backward'");
        }

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, ChatBoost.gameType);
            stmt.setString(2, ChatBoost.gameName);
            stmt.setString(3, time);
            stmt.setInt(4, count);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String messageType = rs.getString("type");
                    String messageName = rs.getString("name");
                    String messageContent = rs.getString("message");
                    String timestamp = rs.getString("timestamp");

                    // 假设你有一个 MessageSql 类来封装查询结果
                    result.add(new MessageSql(messageType, messageName, ChatBoost.json.fromJson(messageContent, Component.class), timestamp));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return result;
    }

    public String queryTime(String message) {
        String query = "SELECT timestamp FROM ChatLogs WHERE type = ? AND name = ? AND message = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {

            stmt.setString(1, ChatBoost.gameType);   // 设置type的值
            stmt.setString(2, ChatBoost.gameName);   // 设置name的值
            stmt.setString(3, message); // 设置message的值

            // 执行查询
            ResultSet rs = stmt.executeQuery();

            // 输出查询结果（timestamp）
            if (rs.next()) {
                return rs.getString("timestamp");
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return null;
    }



    public int messageCount() {
        String query = "SELECT COUNT(*) FROM ChatLogs WHERE type = ? AND name = ?";
        String type = ChatBoost.gameType, name = ChatBoost.gameName;

        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, type);
            stmt.setString(2, name);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                } else {
                    return 0;
                }
            }
        } catch (SQLException e) {
            ChatBoost.Logger.error("[ChatData.messageCount]Error counting messages by type and name", e);
        }

        return 0;
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
