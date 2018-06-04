package org.yamcs.security;

import java.util.concurrent.CompletableFuture;

public interface YamcsSecurityManager {

    /**
     * Checks if the user can be identified with the given credentials.
     * 
     * @return username of the authenticated user
     */
    CompletableFuture<String> validateUser(String username, char[] password);

    // String validateUserAndRole(String username, Set<Role> roles);
}
