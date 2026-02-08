package com.o3.server;

import com.sun.net.httpserver.BasicAuthenticator;
import java.sql.SQLException;

public class UserAuthenticator extends BasicAuthenticator {
    
    public UserAuthenticator(String realm) {
        super(realm);
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        // NOTE: For coursework purposes only - passwords stored in plaintext
        // In production, use proper password hashing (BCrypt, PBKDF2, or Argon2)
        try {
            MessageDatabase db = MessageDatabase.getInstance();
            User user = db.getUser(username);
            if (user != null) {
                return user.getPassword().equals(password);
            }
        } catch (SQLException e) {
            System.err.println("Error checking credentials: " + e.getMessage());
        }
        return false;
    }

    public boolean addUser(String username, String password, String email, String nickname) {
        try {
            MessageDatabase db = MessageDatabase.getInstance();
            return db.addUser(username, password, email, nickname);
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            return false;
        }
    }
}
