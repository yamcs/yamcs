package org.yamcs.security;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class UserCache {

    private Cache<String, User> cache = CacheBuilder.newBuilder()
            .build();

    public User getUserFromCache(String username) {
        return cache.getIfPresent(username);
    }

    public void putUserInCache(User user) {
        cache.put(user.getName(), user);
    }

    public void removeUserFromCache(String username) {
        cache.invalidate(username);
    }
}
