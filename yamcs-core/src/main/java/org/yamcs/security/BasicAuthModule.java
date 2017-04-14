package org.yamcs.security;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.security.cert.X509Certificate;
import java.util.Base64;
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
import org.yamcs.api.MediaType;
import org.yamcs.protobuf.Web.RestExceptionMessage;
import org.yamcs.security.Privilege.Type;
import org.yamcs.web.BadRequestException;
import org.yamcs.web.rest.RestRequest;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.CharsetUtil;

public class BasicAuthModule implements AuthModule {
  private static final Logger log = LoggerFactory.getLogger(BasicAuthModule.class);
  private final Realm realm; 
  private String realmName;
  // time to cache a user entry
  static final int PRIV_CACHE_TIME = 30*1000;
  // time to cache a certificate to username mapping
  private final ConcurrentHashMap<AuthenticationToken, Future<User>> cache = new ConcurrentHashMap<>();
 
  
    
    public BasicAuthModule(Map<String, Object> config) {
        String realmClass = YConfiguration.getString(config, "realm");
        realm = loadRealm(realmClass);
    }
    
    
    private Realm loadRealm(String realmClass) throws ConfigurationException {
        // load the specified class;
        Realm realm;
        try {
            realm = (Realm) Realm.class.getClassLoader()
                    .loadClass(realmClass).newInstance();
            realmName = realm.getClass().getSimpleName();
        } catch (Exception e) {
            throw new ConfigurationException("Unable to load the realm class: " + realmClass, e);
        }
        return realm;
    }
    
    /**
    *
    * @return the roles of the calling user
    */
   public String[] getRoles(final AuthenticationToken authenticationToken) {
       // Load user and read roles from result
       User user = getUser(authenticationToken);
       if(user == null) {
           return null;
       }
       return user.getRoles();
   }
   
   
    @Override
    public CompletableFuture<AuthenticationToken> authenticateHttp(ChannelHandlerContext ctx, HttpRequest req) {
        if (!req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            sendUnauthorized(ctx, req, "No "+HttpHeaderNames.AUTHORIZATION+" header present");
            return completedExceptionally(new AuthorizationPendingException());
        }

        String authorizationHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
        if (!authorizationHeader.startsWith("Basic ")) { // Exact case only
            return completedExceptionally(new BadRequestException("Unsupported Authorization header '" + authorizationHeader + "'"));
        }
        // Get encoded user and password, comes after "Basic "
        String userpassEncoded = authorizationHeader.substring(6);
        String userpassDecoded;
        try {
            userpassDecoded = new String(Base64.getDecoder().decode(userpassEncoded));
        } catch (IllegalArgumentException e) {
            return completedExceptionally( new BadRequestException("Could not decode Base64-encoded credentials"));
        }

        // Username is not allowed to contain ':', but passwords are
        String[] parts = userpassDecoded.split(":", 2);
        if (parts.length < 2) {
            return completedExceptionally( new BadRequestException("Malformed username/password (Not separated by colon?)"));
        }
        AuthenticationToken token = new UsernamePasswordToken(parts[0], parts[1]);
        if (!realm.authenticates(token)) {
            sendUnauthorized(ctx, req, "the realm could not authenticate the provided token");
            return completedExceptionally( new AuthorizationPendingException());
        }
        
        return CompletableFuture.completedFuture(token);
    }
    
    static private CompletableFuture<AuthenticationToken> completedExceptionally(Exception e) {
        CompletableFuture<AuthenticationToken> cf = new CompletableFuture<AuthenticationToken>();
        cf.completeExceptionally(e);
        return cf;
    }
    
    public User getUser(final AuthenticationToken authenticationToken)  {
        while (true) {
            if(authenticationToken == null)
                return null;
            Future<User> f = cache.get(authenticationToken);
            if (f == null) {
                Callable<User> eval = new Callable<User>() {
                    @Override
                    public User call() {
                        try {
                            // check the realm support the type of provided token
                            if (!realm.supports(authenticationToken)) {
                                log.error("Realm {} does not support authentication token of type {}"
                                        ,realmName, authenticationToken.getClass());
                                return null;
                            }
                            return realm.loadUser(authenticationToken);
                        } catch (Exception e) {
                            log.error("Unable to load user from realm {}", realmName, e);
                            return new User(authenticationToken);
                        }
                    }
                };
                FutureTask<User> ft = new FutureTask<User>(eval);
                f = cache.putIfAbsent(authenticationToken, ft);
                if (f == null) {
                    f = ft;
                    ft.run();
                }
            }
            try {
                User u = f.get();
                if ((System.currentTimeMillis() - u.lastUpdated) < PRIV_CACHE_TIME)
                    return u;
                cache.remove(authenticationToken, f); // too old
            } catch (CancellationException e) {
                cache.remove(authenticationToken, f);
            } catch (ExecutionException e) {
                cache.remove(authenticationToken,f); //we don't cache exceptions
                if (e.getCause() instanceof RuntimeException)
                    throw (RuntimeException) e.getCause();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Exception e) {
                log.error("Unable to load user", e);
                return null;
            }
        }

    }

    /**
     *
     * @param type
     * @param privilege
     *            a opsname of tc, tm parameter or tm packet
     * @return true if the privilege is known and the current user has it.
     */
    public boolean hasPrivilege(final AuthenticationToken authenticationToken, Type type, String privilege) {
        User user = getUser(authenticationToken);
        if(user == null) {
            return false;
        }
        return user.hasPrivilege(type, privilege);
    }


    public boolean hasRole(final AuthenticationToken authenticationToken, String role ) {
        // Load user and read role from result
        User user = getUser(authenticationToken);
        if(user == null) {
            return false;
        }
        return user.hasRole(role);
    }
    
    
    private ChannelFuture sendUnauthorized(ChannelHandlerContext ctx, HttpRequest request, String reason) {
        ByteBuf buf;
        MediaType mt = RestRequest.deriveTargetContentType(request);
        if(mt==MediaType.PROTOBUF) {
            RestExceptionMessage rem = RestExceptionMessage.newBuilder().setMsg(HttpResponseStatus.UNAUTHORIZED.toString()).build();
            buf = Unpooled.copiedBuffer(rem.toByteArray());
        } else {
            buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
        }
        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
        res.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, "Basic realm=\"" + Privilege.getAuthModuleName() + "\"");

        log.warn("{} {} {} [realm=\"{}\"]: {}", request.method(), request.uri(), res.status().code(), Privilege.getAuthModuleName(), reason);
        return ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
    }
}
