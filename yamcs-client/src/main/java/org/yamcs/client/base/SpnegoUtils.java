package org.yamcs.client.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import javax.security.auth.Subject;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.AppConfigurationEntry.LoginModuleControlFlag;
import javax.security.auth.login.Configuration;
import javax.security.auth.login.LoginContext;
import javax.security.auth.login.LoginException;

import org.ietf.jgss.GSSContext;
import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.GSSManager;
import org.ietf.jgss.GSSName;
import org.ietf.jgss.Oid;

public final class SpnegoUtils {

    private static final String JAAS_KRB5 = "com.sun.security.auth.module.Krb5LoginModule";

    private static final Oid SPNEGO_OID;
    static {
        try {
            SPNEGO_OID = new Oid("1.3.6.1.5.5.2");
        } catch (GSSException e) {
            throw new RuntimeException(e);
        }
    }

    private static final HostnameVerifier NO_HOSTNAME_VERIFICATION = (hostname, session) -> true;
    private static final TrustManager[] TRUST_ALL_CERTS = new TrustManager[] { new X509TrustManager() {
        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return null;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            // Ignore
        }

        @Override
        public void checkServerTrusted(X509Certificate[] arg0, String arg1) throws CertificateException {
            // Ignore
        }
    } };

    public static synchronized String fetchAuthenticationCode(SpnegoInfo info) throws SpnegoException {
        try {
            byte[] token = createToken(info.getServerURL().getHost(), info.getPrincipal());

            URL url = new URL(info.getServerURL() + "/auth/spnego");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Authorization", "Negotiate " + new String(token));

            if (info.getServerURL().isTLS() && !info.isVerifyTLS()) {
                try {
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(null, TRUST_ALL_CERTS, new java.security.SecureRandom());
                    ((HttpsURLConnection) conn).setSSLSocketFactory(ctx.getSocketFactory());
                    ((HttpsURLConnection) conn).setHostnameVerifier(NO_HOSTNAME_VERIFICATION);
                } catch (KeyManagementException | NoSuchAlgorithmException e) {
                    throw new SpnegoException(e);
                }
            }

            conn.connect();

            if (conn.getResponseCode() == 200) {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    return reader.readLine();
                }
            } else {
                throw new SpnegoException("Unexpected server response " + conn.getResponseCode());
            }
        } catch (PrivilegedActionException e) {
            throw new SpnegoException(e.getCause());
        } catch (LoginException | GSSException | IOException e) {
            throw new SpnegoException(e);
        }
    }

    private static synchronized byte[] createToken(String host, String principal)
            throws LoginException, PrivilegedActionException, GSSException {
        GSSManager gssManager = GSSManager.getInstance();
        GSSName gssName = gssManager.createName("HTTP@" + host, GSSName.NT_HOSTBASED_SERVICE, SPNEGO_OID);
        Subject subject = login(principal);

        GSSContext gssContext = Subject.doAs(subject, (PrivilegedExceptionAction<GSSContext>) () -> {
            GSSCredential credential = gssManager.createCredential(
                    null, GSSCredential.DEFAULT_LIFETIME, SPNEGO_OID, GSSCredential.INITIATE_ONLY);

            GSSContext context = gssManager.createContext(gssName, SPNEGO_OID, credential, GSSContext.DEFAULT_LIFETIME);
            context.requestMutualAuth(true);
            context.requestConf(true);
            context.requestInteg(true);
            context.requestReplayDet(true);
            context.requestSequenceDet(true);
            return context;
        });

        try {
            byte[] token = Subject.doAs(subject, (PrivilegedExceptionAction<byte[]>) () -> {
                return gssContext.initSecContext(new byte[0], 0, 0);
            });
            return Base64.getEncoder().encode(token);
        } finally {
            if (gssContext != null) {
                gssContext.dispose();
            }
        }
    }

    private static Subject login(String principal) throws LoginException {
        Map<String, String> options = new HashMap<>();
        options.put("renewTGT", "true");
        options.put("principal", principal);
        options.put("useTicketCache", "true");
        options.put("doNotPrompt", "true");

        LoginContext context = new LoginContext("", null, null, new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String name) {
                return new AppConfigurationEntry[] { new AppConfigurationEntry(
                        JAAS_KRB5, LoginModuleControlFlag.REQUIRED, options) };
            }
        });

        context.login();
        return context.getSubject();
    }

    @SuppressWarnings("serial")
    static class SpnegoException extends Exception {

        private SpnegoException(String message) {
            super(message);
        }

        private SpnegoException(Throwable cause) {
            super(cause);
        }
    }
}
