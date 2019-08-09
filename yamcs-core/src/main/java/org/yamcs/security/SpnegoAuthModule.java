package org.yamcs.security;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;

import java.io.IOException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.AppConfigurationEntry;
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
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.Spec.OptionType;
import org.yamcs.YConfiguration;
import org.yamcs.http.AuthModuleHttpHandler;
import org.yamcs.http.HttpRequestHandler;
import org.yamcs.utils.StringConverter;

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
    private static final String JAAS_ENTRY_NAME = "YamcsHTTP";
    private static final String JAAS_KRB5 = "com.sun.security.auth.module.Krb5LoginModule";
    private static final String NEGOTIATE = "Negotiate";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final long AUTH_CODE_VALIDITY = 10000;

    private static Oid spnegoOid;
    static {
        try {
            spnegoOid = new Oid("1.3.6.1.5.5.2");
            // krb5Oid = new Oid("1.2.840.113554.1.2.2");
        } catch (GSSException e) {
            throw new ConfigurationException(e);
        }
    }

    private String realm;
    private boolean stripRealm; // if true, realm will be stripped from the username

    private Map<String, SpnegoAuthenticationInfo> code2info = new ConcurrentHashMap<>();

    private LoginContext yamcsLogin;
    private GSSManager gssManager;
    private GSSCredential yamcsCred;

    @Override
    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("keytab", OptionType.STRING).withRequired(true);
        spec.addOption("principal", OptionType.STRING).withRequired(true);
        spec.addOption("stripRealm", OptionType.BOOLEAN).withDefault(true);
        spec.addOption("debug", OptionType.BOOLEAN).withDefault(false);
        return spec;
    }

    @Override
    public void init(YConfiguration args) throws InitException {
        String userPrincipal = args.getString("principal");
        int idx = userPrincipal.lastIndexOf('@');
        if (idx < 0) {
            throw new InitException("SPNEGO principal should take the form HTTP/<host>.<domain>@<REALM>");
        }

        String servicePrincipal = userPrincipal.substring(0, idx);
        realm = userPrincipal.substring(idx + 1);
        stripRealm = args.getBoolean("stripRealm");

        Map<String, String> jaasOpts = new HashMap<>();
        jaasOpts.put("useKeyTab", "true");
        jaasOpts.put("storeKey", "true");
        jaasOpts.put("keyTab", args.getString("keytab"));
        jaasOpts.put("useTicketCache", "true");
        jaasOpts.put("principal", servicePrincipal);
        jaasOpts.put("debug", Boolean.toString(args.getBoolean("debug")));

        AppConfigurationEntry jaasEntry = new AppConfigurationEntry(JAAS_KRB5, REQUIRED, jaasOpts);
        JaasConfiguration.addEntry(JAAS_ENTRY_NAME, jaasEntry);

        try {
            yamcsLogin = new LoginContext(JAAS_ENTRY_NAME, new DummyCallbackHandler());
            yamcsLogin.login();
            gssManager = GSSManager.getInstance();
        } catch (LoginException e) {
            throw new InitException(String.format("Cannot login %s to Kerberos", userPrincipal), e);
        }
    }

    @Override
    public AuthenticationInfo getAuthenticationInfo(AuthenticationToken token) throws AuthenticationException {
        if (token instanceof ThirdPartyAuthorizationCode) {
            return authenticateByCode((ThirdPartyAuthorizationCode) token);
        } else {
            return null;
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
    public boolean verifyValidity(AuthenticationInfo authenticationInfo) {
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
        SECURE_RANDOM.nextBytes(b);
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
                        String userPrincipal = yamcsContext.getSrcName().toString();
                        log.debug("Got GSS Src Name {}", userPrincipal);

                        if (!userPrincipal.endsWith("@" + realm)) {
                            log.warn("User {} does not match realm {}", userPrincipal, realm);
                            HttpRequestHandler.sendPlainTextError(ctx, req, HttpResponseStatus.UNAUTHORIZED);
                            return;
                        }
                        String username = userPrincipal;
                        if (stripRealm) {
                            username = userPrincipal.substring(0, userPrincipal.length() - realm.length() - 1);
                        }
                        SpnegoAuthenticationInfo authInfo = new SpnegoAuthenticationInfo(this, username);
                        authInfo.addExternalIdentity(getClass().getName(), userPrincipal);
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

    private static class SpnegoAuthenticationInfo extends AuthenticationInfo {

        long created; // date of creation

        public SpnegoAuthenticationInfo(AuthModule authenticator, String username) {
            super(authenticator, username);
            this.created = System.currentTimeMillis();
        }
    }

    private static class DummyCallbackHandler implements CallbackHandler {

        @Override
        public void handle(Callback[] callbacks) throws IOException, UnsupportedCallbackException {
            return;
        }
    }
}
