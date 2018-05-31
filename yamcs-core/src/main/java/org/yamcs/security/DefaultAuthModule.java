package org.yamcs.security;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.web.UnauthorizedException;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;



/**
 * Default authentication module that authenticates users from Realm.
 * Authentication is performed based on username/password 
 * 
 * 
 * @author nm
 */
public class DefaultAuthModule extends AbstractAuthModule {
    public static final String USERNAME = "username";
    public static final String PASSWORD = "password";

    private static final Logger log = LoggerFactory.getLogger(DefaultAuthModule.class);

    private final Realm realm;
    private String realmName;

    // time to cache a user entry
    static final int PRIV_CACHE_TIME = 30 * 1000;
    // time to cache a certificate to username mapping
    private final ConcurrentHashMap<AuthenticationToken, Future<User>> cache = new ConcurrentHashMap<>();

    public DefaultAuthModule(Map<String, Object> config) {
        String realmClass = YConfiguration.getString(config, "realm");
        realm = loadRealm(realmClass);
    }

    public Realm getRealm() {
        return realm;
    }

    private Realm loadRealm(String realmClass) throws ConfigurationException {
        // load the specified class;
        Realm realm;
        try {
            realm = (Realm) Realm.class.getClassLoader().loadClass(realmClass).newInstance();
            realmName = realm.getClass().getSimpleName();
        } catch (Exception e) {
            throw new ConfigurationException("Unable to load the realm class: " + realmClass, e);
        }
        return realm;
    }

    /**
     * @return the roles of the calling user
     */

    @Override
    public User getUser(final AuthenticationToken authenticationToken) {
        while (true) {
            if (authenticationToken == null) {
                return null;
            }
            Future<User> f = cache.get(authenticationToken);
            if (f == null) {
                Callable<User> eval = () -> {
                    try {
                        // check the realm support the type of provided token
                        if (!realm.supports(authenticationToken)) {
                            log.error("Realm {} does not support authentication token of type {}", realmName,
                                    authenticationToken.getClass());
                            return null;
                        }
                        return realm.loadUser(authenticationToken.getPrincipal());
                    } catch (Exception e) {
                        log.error("Unable to load user from realm {}", realmName, e);
                        return new User(authenticationToken.getPrincipal());
                    }
                };
                FutureTask<User> ft = new FutureTask<>(eval);
                f = cache.putIfAbsent(authenticationToken, ft);
                if (f == null) {
                    f = ft;
                    ft.run();
                }
            }
            try {
                User u = f.get();
                if ((System.currentTimeMillis() - u.getLastUpdated().getTime()) < PRIV_CACHE_TIME) {
                    return u;
                }
                cache.remove(authenticationToken, f); // too old
            } catch (CancellationException e) {
                cache.remove(authenticationToken, f);
            } catch (ExecutionException e) {
                cache.remove(authenticationToken, f); // we don't cache exceptions
                if (e.getCause() instanceof RuntimeException) {
                    throw (RuntimeException) e.getCause();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("Unable to load user", e);
                return null;
            }
        }

    }

    @Override
    public boolean verifyToken(AuthenticationToken authenticationToken) {
        return getUser(authenticationToken)!=null;
    }

    @Override
    public CompletableFuture<AuthenticationToken> authenticate(String type, Object authObj) {
        CompletableFuture<AuthenticationToken> r = new CompletableFuture<>();
        if(AuthModule.TYPE_USERPASS.equals(type)) {
            try {
                Map<String, String> m = (Map<String, String>) authObj;
                String username = m.get(USERNAME);
                String password = m.get(PASSWORD);
                UsernamePasswordToken token = new UsernamePasswordToken(username, password.toCharArray());
                User u = getUser(token);
                if(u==null) {
                    r.completeExceptionally(new UnauthorizedException());
                } else {
                    r.complete(token);
                }
            } catch (JsonSyntaxException e) {
                r.completeExceptionally(e);
            }
        } else {
            r.completeExceptionally(new ConfigurationException("Unsupported authentication type '"+type+"'"));
        }
        return r;
    }
}
