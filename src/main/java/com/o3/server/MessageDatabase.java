package com.o3.server;

import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public class MessageDatabase {
    private static MessageDatabase instance = null;
    private Connection connection = null;
    
    private MessageDatabase() {
    }
    
    public static synchronized MessageDatabase getInstance() {
        if (instance == null) {
            instance = new MessageDatabase();
        }
        return instance;
    }
    
    public void open(String dbName) throws SQLException {
        try {
            File dbFile = new File(dbName);
            boolean dbExists = dbFile.exists() && !dbFile.isDirectory();
            
            String connectionAddress = "jdbc:sqlite:" + dbName;
            connection = DriverManager.getConnection(connectionAddress);
            
            if (!dbExists) {
                initializeDatabase();
            }
        } catch (SQLException e) {
            System.err.println("Error opening database: " + e.getMessage());
            throw e;
        }
    }
    
    private boolean initializeDatabase() throws SQLException {
        if (null != connection) {
            // Create users table
            String createUsersString = "CREATE TABLE users (" +
                "username TEXT PRIMARY KEY NOT NULL, " +
                "password TEXT NOT NULL, " +
                "email TEXT NOT NULL, " +
                "nickname TEXT NOT NULL)";
            
            Statement createStatement = connection.createStatement();
            createStatement.executeUpdate(createUsersString);
            createStatement.close();
            
            // Create messages table
            String createMessagesString = "CREATE TABLE messages (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "target_body_name TEXT NOT NULL, " +
                "center_body_name TEXT NOT NULL, " +
                "epoch TEXT NOT NULL, " +
                "orbital_elements TEXT, " +
                "state_vector TEXT, " +
                "record_time_received INTEGER NOT NULL, " +
                "record_owner TEXT NOT NULL, " +
                "FOREIGN KEY (record_owner) REFERENCES users(nickname))";
            
            createStatement = connection.createStatement();
            createStatement.executeUpdate(createMessagesString);
            createStatement.close();
            
            return true;
        }
        return false;
    }
    
    public boolean addUser(String username, String password, String email, String nickname) throws SQLException {
        try {
            // Check if user already exists
            String checkQuery = "SELECT username FROM users WHERE username = ?";
            PreparedStatement checkStatement = connection.prepareStatement(checkQuery);
            checkStatement.setString(1, username);
            ResultSet resultSet = checkStatement.executeQuery();
            
            if (resultSet.next()) {
                resultSet.close();
                checkStatement.close();
                return false; // User already exists
            }
            
            resultSet.close();
            checkStatement.close();
            
            // Insert new user
            String insertQuery = "INSERT INTO users (username, password, email, nickname) VALUES (?, ?, ?, ?)";
            PreparedStatement insertStatement = connection.prepareStatement(insertQuery);
            insertStatement.setString(1, username);
            insertStatement.setString(2, password);
            insertStatement.setString(3, email);
            insertStatement.setString(4, nickname);
            insertStatement.executeUpdate();
            insertStatement.close();
            
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            throw e;
        }
    }
    
    public User getUser(String username) throws SQLException {
        String query = "SELECT username, password, email, nickname FROM users WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        ResultSet resultSet = statement.executeQuery();
        
        User user = null;
        if (resultSet.next()) {
            user = new User(
                resultSet.getString("username"),
                resultSet.getString("password"),
                resultSet.getString("email"),
                resultSet.getString("nickname")
            );
        }
        
        resultSet.close();
        statement.close();
        return user;
    }
    
    public String getUserNickname(String username) throws SQLException {
        String query = "SELECT nickname FROM users WHERE username = ?";
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setString(1, username);
        ResultSet resultSet = statement.executeQuery();
        
        String nickname = null;
        if (resultSet.next()) {
            nickname = resultSet.getString("nickname");
        }
        
        resultSet.close();
        statement.close();
        return nickname;
    }
    
    public int addMessage(String targetBodyName, String centerBodyName, String epoch,
                          JSONObject orbitalElements, JSONObject stateVector,
                          String ownerNickname) throws SQLException {
        long timestamp = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();
        
        String insertQuery = "INSERT INTO messages " +
            "(target_body_name, center_body_name, epoch, orbital_elements, state_vector, " +
            "record_time_received, record_owner) VALUES (?, ?, ?, ?, ?, ?, ?)";
        
        PreparedStatement statement = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, targetBodyName);
        statement.setString(2, centerBodyName);
        statement.setString(3, epoch);
        statement.setString(4, orbitalElements != null ? orbitalElements.toString() : null);
        statement.setString(5, stateVector != null ? stateVector.toString() : null);
        statement.setLong(6, timestamp);
        statement.setString(7, ownerNickname);
        
        statement.executeUpdate();
        
        ResultSet generatedKeys = statement.getGeneratedKeys();
        int id = -1;
        if (generatedKeys.next()) {
            id = generatedKeys.getInt(1);
        }
        
        generatedKeys.close();
        statement.close();
        
        return id;
    }
    
    public List<ObservationRecord> getAllMessages() throws SQLException {
        List<ObservationRecord> messages = new ArrayList<>();
        String query = "SELECT id, target_body_name, center_body_name, epoch, orbital_elements, " +
                       "state_vector, record_time_received, record_owner FROM messages";
        
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(query);
        
        while (resultSet.next()) {
            int id = resultSet.getInt("id");
            String targetBodyName = resultSet.getString("target_body_name");
            String centerBodyName = resultSet.getString("center_body_name");
            String epoch = resultSet.getString("epoch");
            
            String orbitalElementsStr = resultSet.getString("orbital_elements");
            JSONObject orbitalElements = orbitalElementsStr != null ? new JSONObject(orbitalElementsStr) : null;
            
            String stateVectorStr = resultSet.getString("state_vector");
            JSONObject stateVector = stateVectorStr != null ? new JSONObject(stateVectorStr) : null;
            
            long recordTimeReceived = resultSet.getLong("record_time_received");
            String recordOwner = resultSet.getString("record_owner");
            
            // Convert timestamp to ISO 8601 format in UTC
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(recordTimeReceived), ZoneOffset.UTC);
            String timestamp = dateTime.toString();
            
            ObservationRecord record = new ObservationRecord(
                targetBodyName, centerBodyName, epoch, orbitalElements, stateVector);
            record.setMetadata(id, timestamp, recordOwner);
            
            messages.add(record);
        }
        
        resultSet.close();
        statement.close();
        
        return messages;
    }
    
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
