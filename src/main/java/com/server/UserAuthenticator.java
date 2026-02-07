package com.server;

import com.sun.net.httpserver.BasicAuthenticator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserAuthenticator extends BasicAuthenticator {
    private Map<String, User> users = null;

    public UserAuthenticator(String realm) {
        super(realm);
        users = new ConcurrentHashMap<String, User>();
        // Add a dummy user for initial testing
        users.put("dummy", new User("dummy", "passwd", "dummy@example.com"));
    }

    @Override
    public boolean checkCredentials(String username, String password) {
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
