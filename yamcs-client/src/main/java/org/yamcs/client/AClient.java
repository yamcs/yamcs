package org.yamcs.client;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class AClient {

    public static void main(String[] args) throws ClientException, NoSuchAlgorithmException, KeyManagementException {
        YamcsClient client = YamcsClient.newBuilder("edr-mcs-sim", 443)
                .withTls(true)
                .withVerifyTls(false)
                // .withConnectionAttempts(10)
                .build();

        // client.connect("admin", "admin".toCharArray());
        client.connectWithKerberos();
        System.out.println("connected");
    }

    public static class HttpsTrustManager implements X509TrustManager {
        private static TrustManager[] trustManagers;
        private static final X509Certificate[] _AcceptedIssuers = new X509Certificate[] {};

        @Override
        public void checkClientTrusted(
                X509Certificate[] x509Certificates, String s)
                throws java.security.cert.CertificateException {

        }

        @Override
        public void checkServerTrusted(
                X509Certificate[] x509Certificates, String s)
                throws java.security.cert.CertificateException {

        }

        public boolean isClientTrusted(X509Certificate[] chain) {
            return true;
        }

        public boolean isServerTrusted(X509Certificate[] chain) {
            return true;
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return _AcceptedIssuers;
        }

        public static void allowAllSSL() {
            HttpsURLConnection.setDefaultHostnameVerifier((arg0, arg1) -> true);

            SSLContext context = null;
            if (trustManagers == null) {
                trustManagers = new TrustManager[] { new HttpsTrustManager() };
            }

            try {
                context = SSLContext.getInstance("TLS");
                context.init(null, trustManagers, new SecureRandom());
            } catch (NoSuchAlgorithmException | KeyManagementException e) {
                e.printStackTrace();
            }

            HttpsURLConnection.setDefaultSSLSocketFactory(context != null ? context.getSocketFactory() : null);
        }
    }
}
