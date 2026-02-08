package com.o3.server;

import com.sun.net.httpserver.BasicAuthenticator;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.SQLException;

public class UserAuthenticator extends BasicAuthenticator {
    
    public UserAuthenticator(String realm) {
        super(realm);
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        try {
            MessageDatabase db = MessageDatabase.getInstance();
            User user = db.getUser(username);
            if (user != null) {
                // Use BCrypt to verify password against stored hash
                return BCrypt.checkpw(password, user.getPassword());
            }
        } catch (SQLException e) {
            System.err.println("Error checking credentials: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Handle invalid hash format (e.g., if migration from plaintext is incomplete)
            System.err.println("Invalid password hash format: " + e.getMessage());
        }
        return false;
    }

    public boolean addUser(String username, String password, String email, String nickname) {
        try {
            MessageDatabase db = MessageDatabase.getInstance();
            // Hash the password using BCrypt with salt
            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());
            return db.addUser(username, hashedPassword, email, nickname);
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            return false;
        }
    }
}
