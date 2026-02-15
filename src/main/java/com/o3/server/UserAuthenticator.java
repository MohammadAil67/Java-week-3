package com.o3.server;

import com.sun.net.httpserver.BasicAuthenticator;
import org.apache.commons.codec.digest.Crypt;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;

public class UserAuthenticator extends BasicAuthenticator {
    private final SecureRandom secureRandom;
    
    public UserAuthenticator(String realm) {
        super(realm);
        this.secureRandom = new SecureRandom();
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        try {
            MessageDatabase db = MessageDatabase.getInstance();
            User user = db.getUser(username);
            if (user != null) {
                String hashedPassword = user.getPassword();
                // Use Crypt to verify password against stored hash
                return hashedPassword.equals(Crypt.crypt(password, hashedPassword));
            }
        } catch (SQLException e) {
            System.err.println("Error checking credentials: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Handle invalid hash format
            System.err.println("Invalid password hash format: " + e.getMessage());
        }
        return false;
    }

    public boolean addUser(String username, String password, String email, String nickname) {
        try {
            MessageDatabase db = MessageDatabase.getInstance();
            // Generate random salt for SHA-512
            byte[] saltBytes = new byte[12];
            secureRandom.nextBytes(saltBytes);
            // Encode to Base64 without padding, which gives exactly 16 characters
            String saltString = Base64.getEncoder().withoutPadding().encodeToString(saltBytes);
            String salt = "$6$" + saltString;
            // Hash the password using Crypt with SHA-512
            String hashedPassword = Crypt.crypt(password, salt);
            return db.addUser(username, hashedPassword, email, nickname);
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            return false;
        }
    }
}
