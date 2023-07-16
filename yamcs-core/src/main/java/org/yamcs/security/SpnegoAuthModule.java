package org.yamcs.security;

import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import static javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag.REQUIRED;

import java.io.IOException;
import java.security.PrivilegedAction;
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
import org.yamcs.http.BadRequestException;
import org.yamcs.http.HandlerContext;
import org.yamcs.http.HttpHandler;
import org.yamcs.http.InternalServerErrorException;
import org.yamcs.http.UnauthorizedException;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFutureListener;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
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
public class SpnegoAuthModule extends HttpHandler implements AuthModule {

    private static final Logger log = LoggerFactory.getLogger(SpnegoAuthModule.class);
    private static final String JAAS_ENTRY_NAME = "YamcsHTTP";
    private static final String JAAS_KRB5 = "com.sun.security.auth.module.Krb5LoginModule";
    private static final String NEGOTIATE = "Negotiate";
    private static final long AUTH_CODE_VALIDITY = 10000;

    private static Oid spnegoOid;
    private static Oid krb5Oid;
    static {
        try {
            spnegoOid = new Oid("1.3.6.1.5.5.2");
            krb5Oid = new Oid("1.2.840.113554.1.2.2");
        } catch (GSSException e) {
            throw new ConfigurationException(e);
        }
    }
    private static Oid[] SUPPORTED_OIDS = new Oid[] { spnegoOid, krb5Oid };

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
        // The lifetime of the client's TGT cannot be verified based on
        // SPNEGO ticket alone, and checking the same ticket multiple times
        // result in a replay error.
        //
        // Returning true here means that we accept requests as long as the
        // access token is valid. SPNEGO logins don't get a refresh token, so
        // they will be forced to request a new authorization token whenever
        // necessary.
        return true;
    }

    @Override
    public AuthorizationInfo getAuthorizationInfo(AuthenticationInfo authenticationInfo) {
        return new AuthorizationInfo();
    }

    private synchronized GSSCredential getGSSCredential() throws GSSException {
        if (yamcsCred == null || yamcsCred.getRemainingLifetime() == 0) {
            yamcsCred = Subject.doAs(yamcsLogin.getSubject(), (PrivilegedAction<GSSCredential>) () -> {
                try {
                    GSSCredential clientCred = gssManager.createCredential(null, 3600, SUPPORTED_OIDS,
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

    @Override
    public boolean requireAuth() {
        return false;
    }

    @Override
    public void handle(HandlerContext ctx) {
        String negotiateHeader = ctx.getCredentials(NEGOTIATE);
        if (negotiateHeader != null) {
            try {
                byte[] spnegoToken = Base64.getDecoder().decode(negotiateHeader);
                GSSCredential cred = getGSSCredential();
                if (cred == null) {
                    throw new InternalServerErrorException("Unexpected GSS error");
                }

                GSSContext yamcsContext = gssManager.createContext(cred);
                yamcsContext.acceptSecContext(spnegoToken, 0, spnegoToken.length);
                if (yamcsContext.isEstablished()) {
                    if (yamcsContext.getSrcName() == null) {
                        log.warn("Unknown user. No TGT?");
                        throw new UnauthorizedException();
                    }
                    String userPrincipal = yamcsContext.getSrcName().toString();
                    log.debug("GSS context initiator {}", userPrincipal);

                    if (!userPrincipal.endsWith("@" + realm)) {
                        log.warn("User {} does not match realm {}", userPrincipal, realm);
                        throw new UnauthorizedException();
                    }
                    String username = userPrincipal;
                    if (stripRealm) {
                        username = userPrincipal.substring(0, userPrincipal.length() - realm.length() - 1);
                    }

                    SpnegoAuthenticationInfo authInfo = new SpnegoAuthenticationInfo(this, username);
                    authInfo.addExternalIdentity(getClass().getName(), userPrincipal);
                    String authorizationCode = CryptoUtils.generateRandomPassword(10);
                    code2info.put(authorizationCode, authInfo);

                    ByteBuf buf = Unpooled.copiedBuffer(authorizationCode, CharsetUtil.UTF_8);
                    HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.OK, buf);
                    HttpUtil.setContentLength(res, buf.readableBytes());
                    ctx.sendResponse(res).addListener(ChannelFutureListener.CLOSE);
                } else {
                    log.warn("Context is not established, multiple rounds needed???");
                    throw new UnauthorizedException();
                }
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Failed to base64 decode the SPNEGO token");
            } catch (GSSException e) {
                log.warn("Failed to establish context with the SPNEGO token from header '{}': ",
                        negotiateHeader, e);
                throw new UnauthorizedException();
            }
        } else {
            ByteBuf buf = Unpooled.copiedBuffer(HttpResponseStatus.UNAUTHORIZED.toString() + "\r\n", CharsetUtil.UTF_8);
            HttpResponse res = new DefaultFullHttpResponse(HTTP_1_1, HttpResponseStatus.UNAUTHORIZED, buf);
            HttpUtil.setContentLength(res, buf.readableBytes());
            res.headers().set(HttpHeaderNames.WWW_AUTHENTICATE, NEGOTIATE);
            ctx.sendResponse(res).addListener(ChannelFutureListener.CLOSE);
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
