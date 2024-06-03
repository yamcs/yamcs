package org.yamcs.security;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class HttpsUrlConnectionUtils {

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

    /**
     * Disables SSL and hostname verification for the provided {@link HttpsURLConnection}
     */
    public static void makeInsecure(HttpsURLConnection conn) throws NoSuchAlgorithmException, KeyManagementException {
        var ctx = SSLContext.getInstance("TLS");
        ctx.init(null, TRUST_ALL_CERTS, new SecureRandom());
        ((HttpsURLConnection) conn).setSSLSocketFactory(ctx.getSocketFactory());
        ((HttpsURLConnection) conn).setHostnameVerifier(NO_HOSTNAME_VERIFICATION);
    }
}
