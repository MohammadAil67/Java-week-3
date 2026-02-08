package com.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class RegistrationHandler implements HttpHandler {
    private final UserAuthenticator authenticator;

    public RegistrationHandler(UserAuthenticator authenticator) {
        this.authenticator = authenticator;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (exchange.getRequestMethod().equalsIgnoreCase("POST")) {
            handlePost(exchange);
        } else {
            // Don't support GET or other methods for registration
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
            if (!json.has("username") || !json.has("password") || !json.has("email") || !json.has("nickname")) {
                sendResponse(exchange, 400, "Missing required fields: username, password, email, nickname");
                return;
            }

            String username = json.getString("username");
            String password = json.getString("password");
            String email = json.getString("email");
            String nickname = json.getString("nickname");

            // Validate fields are not empty
            if (username.trim().isEmpty() || password.trim().isEmpty() || 
                email.trim().isEmpty() || nickname.trim().isEmpty()) {
                sendResponse(exchange, 400, "Fields cannot be empty");
                return;
            }

            // Add user
            if (authenticator.addUser(username, password, email, nickname)) {
                exchange.sendResponseHeaders(200, -1);
            } else {
                sendResponse(exchange, 409, "User already registered");
            }
        } catch (JSONException e) {
            sendResponse(exchange, 400, "Invalid JSON format");
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
}
