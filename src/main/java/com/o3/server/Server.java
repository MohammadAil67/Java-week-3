package com.o3.server;

import com.sun.net.httpserver.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Server implements HttpHandler {

    public static void main(String[] args) {
        try {
            // Validate command line arguments
            if (args.length < 2) {
                System.out.println("Usage: java Server <keystore-path> <keystore-password>");
                return;
            }

            String keystorePath = args[0];
            String keystorePassword = args[1];
            
            // Initialize database
            String dbPath = System.getenv("DATABASE_PATH");
            if (dbPath == null || dbPath.trim().isEmpty()) {
                System.err.println("DATABASE_PATH environment variable not set");
                return;
            }
            
            MessageDatabase db = MessageDatabase.getInstance();
            try {
                db.open(dbPath);
                System.out.println("Database opened successfully at: " + dbPath);
            } catch (SQLException e) {
                System.err.println("Failed to open database: " + e.getMessage());
                return;
            }

            // Create HTTPS server
            HttpsServer server = HttpsServer.create(new InetSocketAddress(8001), 0);

            // Configure SSL context
            SSLContext sslContext = myServerSSLContext(keystorePath, keystorePassword);
            server.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                public void configure(HttpsParameters params) {
                    InetSocketAddress remote = params.getClientAddress();
                    SSLContext c = getSSLContext();
                    SSLParameters sslparams = c.getDefaultSSLParameters();
                    params.setSSLParameters(sslparams);
                }
            });

            // Create authenticator
            UserAuthenticator authenticator = new UserAuthenticator("datarecord");

            // Create context for datarecord with authentication
            HttpContext context = server.createContext("/datarecord", new Server());
            context.setAuthenticator(authenticator);

            // Create context for registration without authentication
            server.createContext("/registration", new RegistrationHandler(authenticator));

            server.setExecutor(null);
            server.start();
            System.out.println("Server started on port 8001");

        } catch (FileNotFoundException e) {
            System.out.println("Certificate not found!");
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static SSLContext myServerSSLContext(String keystorePath, String password) throws Exception {
        char[] passphrase = password.toCharArray();
        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(keystorePath), passphrase);

        KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
        kmf.init(ks, passphrase);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
        tmf.init(ks);

        SSLContext ssl = SSLContext.getInstance("TLS");
        ssl.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        return ssl;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePost(exchange);
        } else if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
            handleGet(exchange);
        } else if (exchange.getRequestMethod().equalsIgnoreCase("PUT")) {
            handlePut(exchange);
        } else {
            // Handle other methods (DELETE, etc.)
            String response = "Not supported";
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(400, bytes.length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
        }
    }

    private void handlePost(HttpExchange exchange) throws IOException {
        try {
            // Check Content-Type
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.equals("application/json")) {
                sendResponse(exchange, 400, "Content-Type must be application/json");
                return;
            }

            // Read request body
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
            String requestBody = reader.lines().collect(Collectors.joining("\n"));
            reader.close();

            // Parse JSON
            JSONObject json = new JSONObject(requestBody);

            // Validate required fields
            if (!json.has("target_body_name") || !json.has("center_body_name") || !json.has("epoch")) {
                sendResponse(exchange, 400, "Missing required fields");
                return;
            }

            // Check that at least one of orbital_elements or state_vector is present
            boolean hasOrbitalElements = json.has("orbital_elements");
            boolean hasStateVector = json.has("state_vector");

            if (!hasOrbitalElements && !hasStateVector) {
                sendResponse(exchange, 400, "Message must contain orbital_elements and/or state_vector");
                return;
            }

            // Extract data
            String targetBodyName = json.getString("target_body_name");
            String centerBodyName = json.getString("center_body_name");
            String epoch = json.getString("epoch");

            // Validate fields are not empty
            if (targetBodyName.trim().isEmpty() || centerBodyName.trim().isEmpty() || epoch.trim().isEmpty()) {
                sendResponse(exchange, 400, "Required fields cannot be empty");
                return;
            }

            JSONObject orbitalElements = hasOrbitalElements ? json.getJSONObject("orbital_elements") : null;
            JSONObject stateVector = hasStateVector ? json.getJSONObject("state_vector") : null;

            // Extract record_payload from metadata - it's mandatory
            String recordPayload = null;
            if (!json.has("metadata")) {
                sendResponse(exchange, 400, "Missing required field: metadata");
                return;
            }
            
            JSONObject metadata = json.getJSONObject("metadata");
            if (!metadata.has("record_payload")) {
                sendResponse(exchange, 400, "Missing required field: metadata.record_payload");
                return;
            }
            
            recordPayload = metadata.getString("record_payload");
            if (recordPayload.trim().isEmpty()) {
                sendResponse(exchange, 400, "record_payload cannot be empty");
                return;
            }
            
            // Get the username from the authenticated principal
            String username = exchange.getPrincipal().getUsername();
            
            // Get the user's nickname from the database
            MessageDatabase db = MessageDatabase.getInstance();
            String userNickname = db.getUserNickname(username);
            
            if (userNickname == null) {
                sendResponse(exchange, 500, "User nickname not found");
                return;
            }
            
            // Extract record_owner from metadata - optional, will auto-fill from authenticated user if not provided
            String recordOwner = null;
            if (metadata.has("record_owner")) {
                recordOwner = metadata.getString("record_owner");
            }
            
            // If record_owner is not provided or is empty, auto-fill from authenticated user's nickname
            if (recordOwner == null || recordOwner.trim().isEmpty()) {
                recordOwner = userNickname;
            }
            
            // Extract observatory information if present
            List<Observatory> observatories = new ArrayList<>();
            if (metadata.has("observatory")) {
                JSONArray observatoryArray = metadata.getJSONArray("observatory");
                for (int i = 0; i < observatoryArray.length(); i++) {
                    JSONObject obsJson = observatoryArray.getJSONObject(i);
                    
                    // Validate observatory fields
                    if (!obsJson.has("latitude") || !obsJson.has("longitude") || !obsJson.has("observatory_name")) {
                        sendResponse(exchange, 400, "Invalid observatory data: missing required fields");
                        return;
                    }
                    
                    double latitude = obsJson.getDouble("latitude");
                    double longitude = obsJson.getDouble("longitude");
                    String observatoryName = obsJson.getString("observatory_name");
                    
                    Observatory obs = new Observatory(latitude, longitude, observatoryName);
                    
                    // Check if observatory_weather field is present (can be any type)
                    if (obsJson.has("observatory_weather")) {
                        // Fetch weather data using the coordinates
                        JSONObject weatherData = WeatherFetcher.fetchWeatherData(latitude, longitude);
                        obs.setWeatherData(
                            weatherData.getDouble("temperature_in_kelvins"),
                            weatherData.getDouble("cloudiness_percentage"),
                            weatherData.getDouble("background_light_volume")
                        );
                    }
                    
                    observatories.add(obs);
                }
            }

            // Validate data types in orbital_elements
            if (orbitalElements != null && !validateOrbitalElements(orbitalElements)) {
                sendResponse(exchange, 400, "Invalid data types in orbital_elements");
                return;
            }

            // Validate data types in state_vector
            if (stateVector != null && !validateStateVector(stateVector)) {
                sendResponse(exchange, 400, "Invalid data types in state_vector");
                return;
            }
            
            // Validate that record_owner matches the authenticated user's nickname (security check)
            // This ensures that if a record_owner was explicitly provided, it matches the authenticated user
            // If record_owner was auto-filled, this validation will pass (but is still necessary for security)
            if (!recordOwner.equals(userNickname)) {
                sendResponse(exchange, 403, "record_owner must match authenticated user's nickname");
                return;
            }

            // Store the message in the database using the validated record_owner
            db.addMessage(targetBodyName, centerBodyName, epoch, orbitalElements, stateVector, recordOwner, recordPayload, observatories);

            // Send success response with 200 OK status
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, -1);

        } catch (JSONException e) {
            sendResponse(exchange, 400, "Invalid JSON format");
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            System.err.println("SQL State: " + e.getSQLState());
            System.err.println("Error Code: " + e.getErrorCode());
            e.printStackTrace();
            sendResponse(exchange, 500, "Database error");
        }
    }

    private void handleGet(HttpExchange exchange) throws IOException {
        try {
            // Get messages from database
            MessageDatabase db = MessageDatabase.getInstance();
            List<ObservationRecord> messages = db.getAllMessages();
            
            // Check if there are no observations
            if (messages.isEmpty()) {
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            // Create JSON array of all observations
            JSONArray responseArray = new JSONArray();
            for (ObservationRecord record : messages) {
                responseArray.put(record.toJSON());
            }

            // Send response
            String responseString = responseArray.toString();
            byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();

        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            sendResponse(exchange, 500, "Database error");
        } catch (Exception e) {
            e.printStackTrace(); // Log error for debugging
            sendResponse(exchange, 500, "Internal server error");
        }
    }

    private void sendResponse(HttpExchange exchange, int code, String message) throws IOException {
        byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(bytes);
        outputStream.flush();
        outputStream.close();
    }

    /**
     * Validates that orbital_elements contains numeric values for all expected fields
     */
    private boolean validateOrbitalElements(JSONObject orbitalElements) {
        try {
            // Expected numeric fields in orbital_elements - all are required
            String[] numericFields = {
                "semi_major_axis_au",
                "eccentricity",
                "inclination_deg",
                "longitude_ascending_node_deg",
                "argument_of_periapsis_deg",
                "mean_anomaly_deg"
            };

            for (String field : numericFields) {
                // All fields must be present
                if (!orbitalElements.has(field)) {
                    return false;
                }
                Object value = orbitalElements.get(field);
                // Check if value is null
                if (value == null || value == JSONObject.NULL) {
                    return false;
                }
                // Check if value is a number (not a string or other type)
                if (!(value instanceof Number)) {
                    return false;
                }
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Validates that state_vector contains arrays of numbers for position and velocity
     */
    private boolean validateStateVector(JSONObject stateVector) {
        try {
            // Both position_au and velocity_au_per_day are required
            if (!stateVector.has("position_au") || !stateVector.has("velocity_au_per_day")) {
                return false;
            }

            // Validate position_au is an array of numbers
            JSONArray positionAu = stateVector.getJSONArray("position_au");
            if (positionAu == null || positionAu.length() == 0) {
                return false;
            }
            for (int i = 0; i < positionAu.length(); i++) {
                Object value = positionAu.get(i);
                if (value == null || value == JSONObject.NULL || !(value instanceof Number)) {
                    return false;
                }
            }

            // Validate velocity_au_per_day is an array of numbers
            JSONArray velocityAuPerDay = stateVector.getJSONArray("velocity_au_per_day");
            if (velocityAuPerDay == null || velocityAuPerDay.length() == 0) {
                return false;
            }
            for (int i = 0; i < velocityAuPerDay.length(); i++) {
                Object value = velocityAuPerDay.get(i);
                if (value == null || value == JSONObject.NULL || !(value instanceof Number)) {
                    return false;
                }
            }

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void handlePut(HttpExchange exchange) throws IOException {
        try {
            // Parse query parameters to get the id
            String query = exchange.getRequestURI().getQuery();
            if (query == null || !query.contains("id=")) {
                sendResponse(exchange, 400, "Missing id parameter");
                return;
            }
            
            int recordId;
            try {
                String idStr = query.substring(query.indexOf("id=") + 3);
                // Handle if there are multiple query parameters
                if (idStr.contains("&")) {
                    idStr = idStr.substring(0, idStr.indexOf("&"));
                }
                recordId = Integer.parseInt(idStr);
            } catch (NumberFormatException e) {
                sendResponse(exchange, 400, "Invalid id parameter");
                return;
            }
            
            // Check Content-Type
            String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
            if (contentType == null || !contentType.equals("application/json")) {
                sendResponse(exchange, 400, "Content-Type must be application/json");
                return;
            }
            
            // Read request body
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8));
            String requestBody = reader.lines().collect(Collectors.joining("\n"));
            reader.close();
            
            // Parse JSON
            JSONObject json = new JSONObject(requestBody);
            
            // Validate required fields
            if (!json.has("target_body_name") || !json.has("center_body_name") || !json.has("epoch")) {
                sendResponse(exchange, 400, "Missing required fields");
                return;
            }
            
            // Check that at least one of orbital_elements or state_vector is present
            boolean hasOrbitalElements = json.has("orbital_elements");
            boolean hasStateVector = json.has("state_vector");
            
            if (!hasOrbitalElements && !hasStateVector) {
                sendResponse(exchange, 400, "Message must contain orbital_elements and/or state_vector");
                return;
            }
            
            // Extract data
            String targetBodyName = json.getString("target_body_name");
            String centerBodyName = json.getString("center_body_name");
            String epoch = json.getString("epoch");
            
            // Validate fields are not empty
            if (targetBodyName.trim().isEmpty() || centerBodyName.trim().isEmpty() || epoch.trim().isEmpty()) {
                sendResponse(exchange, 400, "Required fields cannot be empty");
                return;
            }
            
            JSONObject orbitalElements = hasOrbitalElements ? json.getJSONObject("orbital_elements") : null;
            JSONObject stateVector = hasStateVector ? json.getJSONObject("state_vector") : null;
            
            // Extract metadata - record_payload is mandatory
            if (!json.has("metadata")) {
                sendResponse(exchange, 400, "Missing required field: metadata");
                return;
            }
            
            JSONObject metadata = json.getJSONObject("metadata");
            String recordPayload = null;
            if (metadata.has("record_payload")) {
                recordPayload = metadata.getString("record_payload");
                if (recordPayload.trim().isEmpty()) {
                    sendResponse(exchange, 400, "record_payload cannot be empty");
                    return;
                }
            } else {
                sendResponse(exchange, 400, "Missing required field: metadata.record_payload");
                return;
            }
            
            // Extract update_reason (optional)
            String updateReason = null;
            if (metadata.has("update_reason")) {
                updateReason = metadata.getString("update_reason");
            }
            
            // Extract observatory information if present
            List<Observatory> observatories = new ArrayList<>();
            if (metadata.has("observatory")) {
                JSONArray observatoryArray = metadata.getJSONArray("observatory");
                for (int i = 0; i < observatoryArray.length(); i++) {
                    JSONObject obsJson = observatoryArray.getJSONObject(i);
                    
                    // Validate observatory fields
                    if (!obsJson.has("latitude") || !obsJson.has("longitude") || !obsJson.has("observatory_name")) {
                        sendResponse(exchange, 400, "Invalid observatory data: missing required fields");
                        return;
                    }
                    
                    double latitude = obsJson.getDouble("latitude");
                    double longitude = obsJson.getDouble("longitude");
                    String observatoryName = obsJson.getString("observatory_name");
                    
                    Observatory obs = new Observatory(latitude, longitude, observatoryName);
                    
                    // Check if observatory_weather field is present (can be any type)
                    if (obsJson.has("observatory_weather")) {
                        // Fetch weather data using the coordinates
                        JSONObject weatherData = WeatherFetcher.fetchWeatherData(latitude, longitude);
                        obs.setWeatherData(
                            weatherData.getDouble("temperature_in_kelvins"),
                            weatherData.getDouble("cloudiness_percentage"),
                            weatherData.getDouble("background_light_volume")
                        );
                    }
                    
                    observatories.add(obs);
                }
            }
            
            // Validate data types in orbital_elements
            if (orbitalElements != null && !validateOrbitalElements(orbitalElements)) {
                sendResponse(exchange, 400, "Invalid data types in orbital_elements");
                return;
            }
            
            // Validate data types in state_vector
            if (stateVector != null && !validateStateVector(stateVector)) {
                sendResponse(exchange, 400, "Invalid data types in state_vector");
                return;
            }
            
            // Get the username from the authenticated principal
            String username = exchange.getPrincipal().getUsername();
            
            // Get the nickname from the database
            MessageDatabase db = MessageDatabase.getInstance();
            String nickname = db.getUserNickname(username);
            
            if (nickname == null) {
                sendResponse(exchange, 500, "User nickname not found");
                return;
            }
            
            // Check if the message exists and verify ownership
            ObservationRecord existingRecord = db.getMessageById(recordId);
            if (existingRecord == null) {
                sendResponse(exchange, 404, "Message not found");
                return;
            }
            
            if (!existingRecord.getRecordOwner().equals(nickname)) {
                sendResponse(exchange, 403, "Only the owner can update this message");
                return;
            }
            
            // Update the message in the database
            boolean success = db.updateMessage(recordId, targetBodyName, centerBodyName, epoch, 
                                              orbitalElements, stateVector, recordPayload, 
                                              observatories, updateReason);
            
            if (!success) {
                sendResponse(exchange, 500, "Failed to update message");
                return;
            }
            
            // Retrieve the updated record
            ObservationRecord updatedRecord = db.getMessageById(recordId);
            
            // Send response with the updated record
            String responseString = updatedRecord.toJSON().toString();
            byte[] bytes = responseString.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(bytes.length));
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream outputStream = exchange.getResponseBody();
            outputStream.write(bytes);
            outputStream.flush();
            outputStream.close();
            
        } catch (JSONException e) {
            sendResponse(exchange, 400, "Invalid JSON format");
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
            sendResponse(exchange, 500, "Database error");
        }
    }
}
