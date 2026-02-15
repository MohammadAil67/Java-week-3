package com.o3.server;

import com.sun.net.httpserver.BasicAuthenticator;
import org.apache.commons.codec.digest.Crypt;
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
            // Hash the password using Crypt with SHA-512 (default)
            String hashedPassword = Crypt.crypt(password);
            return db.addUser(username, hashedPassword, email, nickname);
        } catch (SQLException e) {
            System.err.println("Error adding user: " + e.getMessage());
            return false;
        }
    }
}
