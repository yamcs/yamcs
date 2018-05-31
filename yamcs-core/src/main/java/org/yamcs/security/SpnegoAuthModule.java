package org.yamcs.security;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.Oid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.utils.StringConverter;
import org.yamcs.web.AuthModuleHttpHandler;
import org.yamcs.web.HttpRequestHandler;
import org.yamcs.web.UnauthorizedException;

import com.sun.security.auth.callback.TextCallbackHandler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.util.CharsetUtil;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * Implements SPNEGO authentication and uses the {@link Realm} for authorization
 * 
 * @author nm
 *
 */
public class SpnegoAuthModule extends AbstractAuthModule implements AuthModuleHttpHandler {
    final String krbRealm; // if not null, only users from this domain will be accepted
    final boolean stripRealm; // if true, domain has to be not null and will be stripped from the username

    Map<String, SpnegoToken> authCode2token = new ConcurrentHashMap<>();
    final long AUTH_CODE_VALIDITY = 10000;
    private static final Logger log = LoggerFactory.getLogger(SpnegoAuthModule.class);

    private final LoginContext yamcsLogin;
    final GSSManager gssManager;

    GSSCredential yamcsCred;
    //this is used for authorization
    YamlRealm yamlRealm;
    
    static final String NEGOTIATE = "Negotiate";
    static final SecureRandom secureRandom = new SecureRandom();
    
    static Oid krb5Oid;
    static Oid spnegoOid;
    static {
        try {
            spnegoOid = new Oid("1.3.6.1.5.5.2");
            krb5Oid = new Oid("1.2.840.113554.1.2.2");
        } catch (GSSException e) {
            throw new ConfigurationException(e);
        }
    }

    public SpnegoAuthModule(Map<String, Object> config) {
        if(config.containsKey("krb5.conf")) {
            System.setProperty("java.security.krb5.conf", YConfiguration.getString(config, "krb5.conf"));    
        }
        if(config.containsKey("jaas.conf")) {
            System.setProperty("java.security.auth.login.config",  YConfiguration.getString(config, "jaas.conf"));
        }
        stripRealm = YConfiguration.getBoolean(config, "stripRealm", false);
        krbRealm = YConfiguration.getString(config, "krbRealm", null);
        
        yamlRealm = new YamlRealm();
        try {
            yamcsLogin = new LoginContext("Yamcs", new TextCallbackHandler());
            yamcsLogin.login();
            gssManager = GSSManager.getInstance();
        } catch (Exception e) {
            throw new ConfigurationException("Cannot login to kerberos");
        }
    }

    @Override
    public CompletableFuture<AuthenticationToken> authenticate(String type, Object authObj) {
        switch(type) {
        case TYPE_CODE:
            return authenticateByCode((String)authObj);
        case TYPE_USERPASS:
            return authenticateByPassword((Map<String, String>)authObj);
        }
        
        log.error("Unsupported authentication type '{}'", type);
        CompletableFuture<AuthenticationToken> r = new CompletableFuture<AuthenticationToken>();
        
        r.completeExceptionally(new ConfigurationException("Unsupported authentication type '"+type+"'"));
        return r;
    }

    
    
    private CompletableFuture<AuthenticationToken> authenticateByPassword(Map<String, String> m) {
        String username = m.get(DefaultAuthModule.USERNAME);
        char[] password = m.get(DefaultAuthModule.PASSWORD).toCharArray();
        CompletableFuture<AuthenticationToken> r = new CompletableFuture<AuthenticationToken>();
        try {
            LoginContext userLogin = new LoginContext("UserAuth", new UserPassCallbackHandler(username, password));
            userLogin.login();
            r.complete(new UsernamePasswordToken(username, password));
        } catch (LoginException e) {
            r.completeExceptionally(e);
        }
        
        return r;
    }

    private CompletableFuture<AuthenticationToken> authenticateByCode(String authorizationCode) {
        CompletableFuture<AuthenticationToken> r = new CompletableFuture<AuthenticationToken>();
        SpnegoToken token = authCode2token.get(authorizationCode);
        long now = System.currentTimeMillis();
        if ((token == null) || (now - token.created) > AUTH_CODE_VALIDITY) {
            r.completeExceptionally(new UnauthorizedException("Invalid authorization code"));
        } else {
            r.complete(token);
        }
        return r;
    }

    @Override
    public boolean verifyToken(AuthenticationToken authenticationToken) {
        // TODO check expiration
        return true;
        
        
    }

    @Override
    public User getUser(AuthenticationToken authToken) {
        yamlRealm.authenticate(authToken);
        if (authToken instanceof SpnegoToken) {
            return ((SpnegoToken)authToken).user;
        } else if (authToken instanceof UsernamePasswordToken) {
            return yamlRealm.loadUser(((UsernamePasswordToken)authToken).getUsername());
        } else {
            return null;
        }
    }

  
    @Override
    public String path() {
        return "spnego";
    }

    private static String generateAuthCode() {
        byte[] b = new byte[16];
        secureRandom.nextBytes(b);
        return StringConverter.arrayToHexString(b);
    }
    private synchronized GSSCredential getGSSCredential() throws GSSException {
        if(yamcsCred==null || yamcsCred.getRemainingLifetime()==0) {
            yamcsCred = Subject.doAs(yamcsLogin.getSubject(), new PrivilegedAction<GSSCredential>() {
                @Override
                public GSSCredential run() {
                    try {
                        GSSCredential clientCred = gssManager.createCredential(null, 3600, spnegoOid, GSSCredential.ACCEPT_ONLY);
                        return clientCred;
                    } catch (Exception e) {
                        log.warn("Failed to get GSS credential", e);
                    }
                    return null;
                }
            });
        }
        return yamcsCred;
    }

    /**
     * Implements the /auth/spnego handler 
     */
    @Override
    public void handle(ChannelHandlerContext ctx, FullHttpRequest req) {
        if (req.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            String authorizationHeader = req.headers().get(HttpHeaderNames.AUTHORIZATION);
            if (authorizationHeader.startsWith(NEGOTIATE)) {
                try {
                    byte[] spnegoToken = Base64.getDecoder().decode(authorizationHeader.substring(NEGOTIATE.length()+1));
                    GSSCredential cred = getGSSCredential();
                    if(cred==null) {
                        HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                        return;
                    }
                    
                    GSSContext yamcsContext = gssManager.createContext(cred);
                    yamcsContext.acceptSecContext(spnegoToken, 0, spnegoToken.length);
                    if(yamcsContext.isEstablished()) {
                        String client = yamcsContext.getSrcName().toString();
                        log.debug("Got GSS Src Name {}", client);

                        if ((krbRealm!=null) && (!client.endsWith("@"+krbRealm))) {
                            log.warn("user {} does not match the defined realm {}", client, krbRealm);
                            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                            return;
                        }
                        if(stripRealm) {
                            client = client.substring(0, client.length()-krbRealm.length()-1);
                        }
                        User user;
                        try {
                            user = yamlRealm.loadUser(client);
                        } catch (ConfigurationException e) {
                            log.warn("Failed to load user {} from credentials.yaml: {}", client, e.getMessage());
                            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                            return;
                        }
                        SpnegoToken token = new SpnegoToken(user);
                        String authorizationCode = generateAuthCode();
                        authCode2token.put(authorizationCode, token);
                        ByteBuf buf = Unpooled.copiedBuffer(authorizationCode, CharsetUtil.UTF_8);
                        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, buf);
                        HttpUtil.setContentLength(res, buf.readableBytes());
                        log.info("{} {} {} Sending Authorization code {}", req.method(), req.uri(), res.status().code(), authorizationCode);
                        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
                    } else {
                        log.warn("context is not estabilished, multiple rounds needed???");
                        HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Failed to base64 decode the SPENGO token: {}", e.getMessage());
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
                } catch (GSSException e) {
                    log.warn("Failed to estabilish context with the SPENGO token from header '{}': ", authorizationHeader, e);
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                } 
            }
        } else {
            ByteBuf buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
            HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
            HttpUtil.setContentLength(res, buf.readableBytes());
            res.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, NEGOTIATE);
           

            log.info("{} {} {} Sending Authenticate Negociate", req.method(), req.uri(), res.status().code());
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
        }
    }
    
    
    static class SpnegoToken implements AuthenticationToken {
        User user;
        long created; // date of creation

        public SpnegoToken(User user) {
            this.user = user;
            this.created = System.currentTimeMillis();
        }

        @Override
        public String getPrincipal() {
            return user.getPrincipalName();
        }
    }
    
   static class UserPassCallbackHandler implements CallbackHandler {
        private char[] password;
        private String username;

        public UserPassCallbackHandler(String name, char[] password) {
            super();
            this.username = name;
            this.password = password;
        }

        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback && username != null) {
                    NameCallback nc = (NameCallback) callback;
                    nc.setName(username);
                } else if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;
                    pc.setPassword(password);
                } else {
                   log.warn("Unrecognized callback "+callback);
                }
            }
        }
    }

}
