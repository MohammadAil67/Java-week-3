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
                "record_payload TEXT, " +
                "record_time_received INTEGER NOT NULL, " +
                "record_owner TEXT NOT NULL, " +
                "update_reason TEXT, " +
                "edited INTEGER)";
            
            createStatement = connection.createStatement();
            createStatement.executeUpdate(createMessagesString);
            createStatement.close();
            
            // Create observatories table
            String createObservatoriesString = "CREATE TABLE observatories (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "message_id INTEGER NOT NULL, " +
                "latitude REAL NOT NULL, " +
                "longitude REAL NOT NULL, " +
                "observatory_name TEXT NOT NULL, " +
                "temperature_in_kelvins REAL, " +
                "cloudiness_percentage REAL, " +
                "background_light_volume REAL, " +
                "FOREIGN KEY (message_id) REFERENCES messages(id))";
            
            createStatement = connection.createStatement();
            createStatement.executeUpdate(createObservatoriesString);
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
                          String ownerNickname, String recordPayload, List<Observatory> observatories) throws SQLException {
        long timestamp = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();
        
        String insertQuery = "INSERT INTO messages " +
            "(target_body_name, center_body_name, epoch, orbital_elements, state_vector, " +
            "record_payload, record_time_received, record_owner) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        PreparedStatement statement = connection.prepareStatement(insertQuery, Statement.RETURN_GENERATED_KEYS);
        statement.setString(1, targetBodyName);
        statement.setString(2, centerBodyName);
        statement.setString(3, epoch);
        statement.setString(4, orbitalElements != null ? orbitalElements.toString() : null);
        statement.setString(5, stateVector != null ? stateVector.toString() : null);
        statement.setString(6, recordPayload);
        statement.setLong(7, timestamp);
        statement.setString(8, ownerNickname);
        
        statement.executeUpdate();
        
        ResultSet generatedKeys = statement.getGeneratedKeys();
        int id = -1;
        if (generatedKeys.next()) {
            id = generatedKeys.getInt(1);
        }
        
        generatedKeys.close();
        statement.close();
        
        // Add observatories if present
        if (observatories != null && !observatories.isEmpty() && id != -1) {
            String insertObsQuery = "INSERT INTO observatories " +
                "(message_id, latitude, longitude, observatory_name, temperature_in_kelvins, cloudiness_percentage, background_light_volume) VALUES (?, ?, ?, ?, ?, ?, ?)";
            PreparedStatement obsStatement = connection.prepareStatement(insertObsQuery);
            
            for (Observatory obs : observatories) {
                obsStatement.setInt(1, id);
                obsStatement.setDouble(2, obs.getLatitude());
                obsStatement.setDouble(3, obs.getLongitude());
                obsStatement.setString(4, obs.getObservatoryName());
                
                // Set weather data (can be null)
                if (obs.getTemperatureInKelvins() != null) {
                    obsStatement.setDouble(5, obs.getTemperatureInKelvins());
                } else {
                    obsStatement.setNull(5, java.sql.Types.REAL);
                }
                if (obs.getCloudinessPercentage() != null) {
                    obsStatement.setDouble(6, obs.getCloudinessPercentage());
                } else {
                    obsStatement.setNull(6, java.sql.Types.REAL);
                }
                if (obs.getBackgroundLightVolume() != null) {
                    obsStatement.setDouble(7, obs.getBackgroundLightVolume());
                } else {
                    obsStatement.setNull(7, java.sql.Types.REAL);
                }
                
                obsStatement.addBatch();
            }
            
            obsStatement.executeBatch();
            obsStatement.close();
        }
        
        return id;
    }
    
    public List<ObservationRecord> getAllMessages() throws SQLException {
        List<ObservationRecord> messages = new ArrayList<>();
        String query = "SELECT id, target_body_name, center_body_name, epoch, orbital_elements, " +
                       "state_vector, record_payload, record_time_received, record_owner, update_reason, edited FROM messages";
        
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
            
            String recordPayload = resultSet.getString("record_payload");
            
            long recordTimeReceived = resultSet.getLong("record_time_received");
            String recordOwner = resultSet.getString("record_owner");
            
            String updateReason = resultSet.getString("update_reason");
            
            // Get edited timestamp - handle NULL
            long editedTimestamp = resultSet.getLong("edited");
            boolean hasEdited = !resultSet.wasNull();
            
            // Convert timestamp to ISO 8601 format in UTC
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(recordTimeReceived), ZoneOffset.UTC);
            String timestamp = dateTime.toString();
            
            ObservationRecord record = new ObservationRecord(
                targetBodyName, centerBodyName, epoch, orbitalElements, stateVector);
            record.setMetadata(id, timestamp, recordOwner);
            record.setRecordPayload(recordPayload);
            
            // Set update_reason if present
            if (updateReason != null) {
                record.setUpdateReason(updateReason);
            }
            
            // Set edited timestamp if present
            if (hasEdited) {
                ZonedDateTime editedDateTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(editedTimestamp), ZoneOffset.UTC);
                record.setEdited(editedDateTime.toString());
            }
            
            // Retrieve observatories for this message
            List<Observatory> observatories = getObservatoriesForMessage(id);
            record.setObservatories(observatories);
            
            messages.add(record);
        }
        
        resultSet.close();
        statement.close();
        
        return messages;
    }
    
    private List<Observatory> getObservatoriesForMessage(int messageId) throws SQLException {
        List<Observatory> observatories = new ArrayList<>();
        String query = "SELECT latitude, longitude, observatory_name, temperature_in_kelvins, cloudiness_percentage, background_light_volume FROM observatories WHERE message_id = ?";
        
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, messageId);
        ResultSet resultSet = statement.executeQuery();
        
        while (resultSet.next()) {
            double latitude = resultSet.getDouble("latitude");
            double longitude = resultSet.getDouble("longitude");
            String observatoryName = resultSet.getString("observatory_name");
            
            Observatory obs = new Observatory(latitude, longitude, observatoryName);
            
            // Set weather data if available
            Double temperature = resultSet.getObject("temperature_in_kelvins", Double.class);
            Double cloudiness = resultSet.getObject("cloudiness_percentage", Double.class);
            Double backgroundLight = resultSet.getObject("background_light_volume", Double.class);
            
            if (temperature != null && cloudiness != null && backgroundLight != null) {
                obs.setWeatherData(temperature, cloudiness, backgroundLight);
            }
            
            observatories.add(obs);
        }
        
        resultSet.close();
        statement.close();
        
        return observatories;
    }
    
    public ObservationRecord getMessageById(int messageId) throws SQLException {
        String query = "SELECT id, target_body_name, center_body_name, epoch, orbital_elements, " +
                       "state_vector, record_payload, record_time_received, record_owner, update_reason, edited FROM messages WHERE id = ?";
        
        PreparedStatement statement = connection.prepareStatement(query);
        statement.setInt(1, messageId);
        ResultSet resultSet = statement.executeQuery();
        
        ObservationRecord record = null;
        if (resultSet.next()) {
            int id = resultSet.getInt("id");
            String targetBodyName = resultSet.getString("target_body_name");
            String centerBodyName = resultSet.getString("center_body_name");
            String epoch = resultSet.getString("epoch");
            
            String orbitalElementsStr = resultSet.getString("orbital_elements");
            JSONObject orbitalElements = orbitalElementsStr != null ? new JSONObject(orbitalElementsStr) : null;
            
            String stateVectorStr = resultSet.getString("state_vector");
            JSONObject stateVector = stateVectorStr != null ? new JSONObject(stateVectorStr) : null;
            
            String recordPayload = resultSet.getString("record_payload");
            long recordTimeReceived = resultSet.getLong("record_time_received");
            String recordOwner = resultSet.getString("record_owner");
            String updateReason = resultSet.getString("update_reason");
            long editedTimestamp = resultSet.getLong("edited");
            boolean hasEdited = !resultSet.wasNull();
            
            // Convert timestamp to ISO 8601 format in UTC
            ZonedDateTime dateTime = ZonedDateTime.ofInstant(
                Instant.ofEpochMilli(recordTimeReceived), ZoneOffset.UTC);
            String timestamp = dateTime.toString();
            
            record = new ObservationRecord(targetBodyName, centerBodyName, epoch, orbitalElements, stateVector);
            record.setMetadata(id, timestamp, recordOwner);
            record.setRecordPayload(recordPayload);
            
            if (updateReason != null) {
                record.setUpdateReason(updateReason);
            }
            
            if (hasEdited) {
                ZonedDateTime editedDateTime = ZonedDateTime.ofInstant(
                    Instant.ofEpochMilli(editedTimestamp), ZoneOffset.UTC);
                record.setEdited(editedDateTime.toString());
            }
            
            // Retrieve observatories for this message
            List<Observatory> observatories = getObservatoriesForMessage(id);
            record.setObservatories(observatories);
        }
        
        resultSet.close();
        statement.close();
        
        return record;
    }
    
    public boolean updateMessage(int messageId, String targetBodyName, String centerBodyName, String epoch,
                                 JSONObject orbitalElements, JSONObject stateVector, String recordPayload,
                                 List<Observatory> observatories, String updateReason) throws SQLException {
        long editedTimestamp = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();
        
        // Set default value for update_reason if not provided
        String finalUpdateReason = (updateReason == null || updateReason.trim().isEmpty()) ? "N/A" : updateReason;
        
        String updateQuery = "UPDATE messages SET " +
            "target_body_name = ?, center_body_name = ?, epoch = ?, " +
            "orbital_elements = ?, state_vector = ?, record_payload = ?, " +
            "update_reason = ?, edited = ? WHERE id = ?";
        
        PreparedStatement statement = connection.prepareStatement(updateQuery);
        statement.setString(1, targetBodyName);
        statement.setString(2, centerBodyName);
        statement.setString(3, epoch);
        statement.setString(4, orbitalElements != null ? orbitalElements.toString() : null);
        statement.setString(5, stateVector != null ? stateVector.toString() : null);
        statement.setString(6, recordPayload);
        statement.setString(7, finalUpdateReason);
        statement.setLong(8, editedTimestamp);
        statement.setInt(9, messageId);
        
        int rowsAffected = statement.executeUpdate();
        statement.close();
        
        if (rowsAffected > 0) {
            // Delete existing observatories for this message
            String deleteObsQuery = "DELETE FROM observatories WHERE message_id = ?";
            PreparedStatement deleteStatement = connection.prepareStatement(deleteObsQuery);
            deleteStatement.setInt(1, messageId);
            deleteStatement.executeUpdate();
            deleteStatement.close();
            
            // Add new observatories
            if (observatories != null && !observatories.isEmpty()) {
                String insertObsQuery = "INSERT INTO observatories " +
                    "(message_id, latitude, longitude, observatory_name, temperature_in_kelvins, cloudiness_percentage, background_light_volume) VALUES (?, ?, ?, ?, ?, ?, ?)";
                PreparedStatement obsStatement = connection.prepareStatement(insertObsQuery);
                
                for (Observatory obs : observatories) {
                    obsStatement.setInt(1, messageId);
                    obsStatement.setDouble(2, obs.getLatitude());
                    obsStatement.setDouble(3, obs.getLongitude());
                    obsStatement.setString(4, obs.getObservatoryName());
                    
                    if (obs.getTemperatureInKelvins() != null) {
                        obsStatement.setDouble(5, obs.getTemperatureInKelvins());
                    } else {
                        obsStatement.setNull(5, java.sql.Types.REAL);
                    }
                    if (obs.getCloudinessPercentage() != null) {
                        obsStatement.setDouble(6, obs.getCloudinessPercentage());
                    } else {
                        obsStatement.setNull(6, java.sql.Types.REAL);
                    }
                    if (obs.getBackgroundLightVolume() != null) {
                        obsStatement.setDouble(7, obs.getBackgroundLightVolume());
                    } else {
                        obsStatement.setNull(7, java.sql.Types.REAL);
                    }
                    
                    obsStatement.addBatch();
                }
                
                obsStatement.executeBatch();
                obsStatement.close();
            }
            
            return true;
        }
        
        return false;
    }
    
    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }
}
