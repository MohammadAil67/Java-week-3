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

            // Store the message in the database
            db.addMessage(targetBodyName, centerBodyName, epoch, orbitalElements, stateVector, nickname);

            // Send success response
            exchange.sendResponseHeaders(200, -1);

        } catch (JSONException e) {
            sendResponse(exchange, 400, "Invalid JSON format");
        } catch (SQLException e) {
            System.err.println("Database error: " + e.getMessage());
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
}
