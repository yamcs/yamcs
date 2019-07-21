package org.yamcs.security;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AccountNotFoundException;
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
import org.yamcs.api.InitException;
import org.yamcs.api.Spec;
import org.yamcs.api.Spec.OptionType;
import org.yamcs.utils.StringConverter;
import org.yamcs.web.AuthModuleHttpHandler;
import org.yamcs.web.HttpRequestHandler;

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

/**
 * Implements SPNEGO authentication against an external Kerberos host.
 * <p>
 * Upon succesful authentication, Kerberos issues a 'ticket' with limited lifetime. {@link SpnegoAuthModule} maps this
 * ticket to an internally generated authorization code which can be used for repeat identity checks against the
 * {@link SecurityStore}.
 * 
 * @author nm
 */
public class SpnegoAuthModule implements AuthModule, AuthModuleHttpHandler {

    private static final Logger log = LoggerFactory.getLogger(SpnegoAuthModule.class);

    private static final String NEGOTIATE = "Negotiate";
    private static final SecureRandom secureRandom = new SecureRandom();

    private static Oid krb5Oid;
    private static Oid spnegoOid;
    static {
        try {
            spnegoOid = new Oid("1.3.6.1.5.5.2");
            krb5Oid = new Oid("1.2.840.113554.1.2.2");
        } catch (GSSException e) {
            throw new ConfigurationException(e);
        }
    }

    private String krbRealm; // if not null, only users from this domain will be accepted
    private boolean stripRealm; // if true, domain has to be not null and will be stripped from the username

    private Map<String, SpnegoAuthenticationInfo> code2info = new ConcurrentHashMap<>();
    private final long AUTH_CODE_VALIDITY = 10000;

    private LoginContext yamcsLogin;
    private GSSManager gssManager;

    private GSSCredential yamcsCred;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("krb5.conf", OptionType.STRING);
        spec.addOption("jaas.conf", OptionType.STRING);
        spec.addOption("krbRealm", OptionType.STRING);
        spec.addOption("stripRealm", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        if (args.containsKey("krb5.conf")) {
            System.setProperty("java.security.krb5.conf", args.getString("krb5.conf"));
        }
        if (args.containsKey("jaas.conf")) {
            System.setProperty("java.security.auth.login.config", args.getString("jaas.conf"));
        }
        if (args.containsKey("krbRealm")) {
            krbRealm = args.getString("krbRealm");
        }
        stripRealm = args.getBoolean("stripRealm");

        try {
            yamcsLogin = new LoginContext("Yamcs", new TextCallbackHandler());
            yamcsLogin.login();
            gssManager = GSSManager.getInstance();
        } catch (Exception e) {
            throw new ConfigurationException("Cannot login to kerberos", e);
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof UsernamePasswordToken) {
            return authenticateByPassword((UsernamePasswordToken) token);
        } else if (token instanceof ThirdPartyAuthorizationCode) {
            return authenticateByCode((ThirdPartyAuthorizationCode) token);
        } else {
            return null;
        }
    }

    private AuthenticationInfo authenticateByPassword(UsernamePasswordToken token) throws AuthenticationException {
        String username = token.getPrincipal();
        char[] password = token.getPassword();
        try {
            LoginContext userLogin = new LoginContext("UserAuth", new UserPassCallbackHandler(username, password));
            userLogin.login();
            return new AuthenticationInfo(this, username);
        } catch (AccountNotFoundException e) {
            return null;
        } catch (LoginException e) {
            throw new AuthenticationException(e);
        }
    }

    private AuthenticationInfo authenticateByCode(ThirdPartyAuthorizationCode code) throws AuthenticationException {
        SpnegoAuthenticationInfo authInfo = code2info.get(code.getPrincipal());
        long now = System.currentTimeMillis();
        if ((authInfo == null) || (now - authInfo.created) > AUTH_CODE_VALIDITY) {
            throw new AuthenticationException("Invalid authorization code");
        } else {
            return authInfo;
        }
    }

    @Override
    public boolean verifyValidity(User user) {
        // TODO check expiration
        return true;
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) {
        return new AuthorizationInfo();
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
        if (yamcsCred == null || yamcsCred.getRemainingLifetime() == 0) {
            yamcsCred = Subject.doAs(yamcsLogin.getSubject(), (PrivilegedAction<GSSCredential>) () -> {
                try {
                    GSSCredential clientCred = gssManager.createCredential(null, 3600, spnegoOid,
                            GSSCredential.ACCEPT_ONLY);
                    return clientCred;
                } catch (Exception e) {
                    log.warn("Failed to get GSS credential", e);
                }
                return null;
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
                    byte[] spnegoToken = Base64.getDecoder()
                            .decode(authorizationHeader.substring(NEGOTIATE.length() + 1));
                    GSSCredential cred = getGSSCredential();
                    if (cred == null) {
                        HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                        return;
                    }

                    GSSContext yamcsContext = gssManager.createContext(cred);
                    yamcsContext.acceptSecContext(spnegoToken, 0, spnegoToken.length);
                    if (yamcsContext.isEstablished()) {
                        String client = yamcsContext.getSrcName().toString();
                        log.debug("Got GSS Src Name {}", client);

                        if ((krbRealm != null) && (!client.endsWith("@" + krbRealm))) {
                            log.warn("User {} does not match the defined realm {}", client, krbRealm);
                            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                            return;
                        }
                        if (stripRealm) {
                            client = client.substring(0, client.length() - krbRealm.length() - 1);
                        }
                        SpnegoAuthenticationInfo authInfo = new SpnegoAuthenticationInfo(this, client);
                        String authorizationCode = generateAuthCode();
                        code2info.put(authorizationCode, authInfo);
                        ByteBuf buf = Unpooled.copiedBuffer(authorizationCode, CharsetUtil.UTF_8);
                        HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, buf);
                        HttpUtil.setContentLength(res, buf.readableBytes());
                        log.info("{} {} {} Sending authorization code {}", req.method(), req.uri(), res.status().code(),
                                authorizationCode);
                        ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
                    } else {
                        log.warn("Context is not established, multiple rounds needed???");
                        HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Failed to base64 decode the SPNEGO token: {}", e.getMessage());
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.BAD_REQUEST);
                } catch (GSSException e) {
                    log.warn("Failed to establish context with the SPNEGO token from header '{}': ",
                            authorizationHeader, e);
                    HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                }
            }
        } else {
            ByteBuf buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
            HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
            HttpUtil.setContentLength(res, buf.readableBytes());
            res.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, NEGOTIATE);

            log.info("{} {} {} Sending WWW-Authenticate: Negotiate", req.method(), req.uri(), res.status().code());
            ctx.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE);
        }
    }

    static class SpnegoAuthenticationInfo extends AuthenticationInfo {

        long created; // date of creation

        public SpnegoAuthenticationInfo(AuthModule authenticator, String principal) {
            super(authenticator, principal);
            this.created = System.currentTimeMillis();
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

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {

            for (Callback callback : callbacks) {
                if (callback instanceof NameCallback && username != null) {
                    NameCallback nc = (NameCallback) callback;
                    nc.setName(username);
                } else if (callback instanceof PasswordCallback) {
                    PasswordCallback pc = (PasswordCallback) callback;
                    pc.setPassword(password);
                } else {
                    log.warn("Unrecognized callback " + callback);
                }
            }
        }
    }
}
