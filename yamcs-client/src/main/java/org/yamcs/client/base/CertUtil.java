package org.yamcs.client.base;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

public class CertUtil {

    public static KeyStore loadCertFile(String caCertFile) throws IOException, GeneralSecurityException {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        KeyStore caKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        caKeyStore.load(null, null);
        try (FileInputStream fis = new FileInputStream(caCertFile)) {
            int i = 0;
            while (fis.available() > 0) {
                X509Certificate cer = (X509Certificate) cf.generateCertificate(fis);
                caKeyStore.setCertificateEntry("cacert" + (i++), cer);
            }
            if (i == 0) {
                throw new IOException("No certificate could be loaded from '" + caCertFile + "'");
            }
        }
        return caKeyStore;
    }
}
