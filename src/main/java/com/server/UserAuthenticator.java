package com.server;

import com.sun.net.httpserver.BasicAuthenticator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserAuthenticator extends BasicAuthenticator {
    private final Map<String, User> users = new ConcurrentHashMap<>();

    public UserAuthenticator(String realm) {
        super(realm);
        // Add a dummy user for initial testing
        users.put("dummy", new User("dummy", "passwd", "dummy@example.com"));
    }

    @Override
    public boolean checkCredentials(String username, String password) {
        // NOTE: For coursework purposes only - passwords stored in plaintext
        // In production, use proper password hashing (BCrypt, PBKDF2, or Argon2)
        if (users.containsKey(username)) {
            User user = users.get(username);
            return user.getPassword().equals(password);
        }
        return false;
    }

    public boolean addUser(String username, String password, String email) {
        // Don't allow registering the same username twice
        if (users.containsKey(username)) {
            return false;
        }
        users.put(username, new User(username, password, email));
        return true;
    }
}
