package org.yamcs.security;

import java.security.cert.X509Certificate;

/**
 * Created by msc on 05/05/15.
 */
public class CertificateToken implements AuthenticationToken {



    private final X509Certificate cert;

    /**
     * Constructor
     * @param cert
     */
    public CertificateToken(X509Certificate cert)
    {
        this.cert = cert;
    }

    @Override
    public Object getPrincipal() {
        return cert.getSubjectX500Principal().getName();
    }

    @Override
    public Object getCredentials() {
        return cert;
    }

    public X509Certificate getCert() {
        return cert;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CertificateToken that = (CertificateToken) o;

        if (cert != null ? !cert.equals(that.cert) : that.cert != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return cert != null ? cert.hashCode() : 0;
    }
}
