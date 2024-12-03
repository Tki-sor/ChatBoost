package com.tkisor.chatboost.data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public interface DatabaseAdapter {
    Connection getConnection() throws SQLException;
    void initialize() throws SQLException;
    void insert(String type, String name, String message, String timestamp) throws SQLException;
    List<MessageSql> query(String type, String name) throws SQLException;
    void close() throws SQLException;
}
