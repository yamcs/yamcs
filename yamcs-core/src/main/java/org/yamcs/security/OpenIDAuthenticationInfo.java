package org.yamcs.security;

import java.util.Objects;

import com.google.gson.JsonObject;

public class OpenIDAuthenticationInfo extends AuthenticationInfo {

    // Make available for extensions that maybe want to add roles.
    public String idToken;
    public String accessToken;
    public String refreshToken;

    // Redirect URI for use in outgoing requests
    public String redirectUri;

    // When the ID Token expires
    public long expiresAt;

    private final ExternalSubject issuerSub; // Subject Identifier at the issuer
    private final ExternalSession issuerSid; // Session ID at the issuer (optional)

    public OpenIDAuthenticationInfo(AuthModule authenticator, String redirectUri,
            String idToken, String accessToken, String refreshToken, String username,
            JsonObject claims) {
        super(authenticator, username);
        this.redirectUri = redirectUri;
        this.idToken = idToken;
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;

        expiresAt = claims.get("exp").getAsLong() * 1000L;

        var iss = claims.get("iss").getAsString();
        var sub = claims.get("sub").getAsString();
        issuerSub = new ExternalSubject(iss, sub);

        if (claims.has("sid")) {
            var sid = claims.get("sid").getAsString();
            issuerSid = new ExternalSession(iss, sid);
        } else {
            this.issuerSid = null;
        }

        // According to OpenID spec, only the combination of "iss" with "sub" is a
        // reasonably unique identifier.
        var externalId = new JsonObject();
        externalId.addProperty("iss", iss);
        externalId.addProperty("sub", sub);
        addExternalIdentity(authenticator.getClass().getName(), externalId.toString());
    }

    /**
     * Returns the subject of the ID Token
     */
    public ExternalSubject getIssuerSub() {
        return issuerSub;
    }

    /**
     * Returns the session id for the ID Token. This may be null, if the ID Token did not contains this claim.
     */
    public ExternalSession getIssuerSid() {
        return issuerSid;
    }

    public interface ExternalClaim {
    }

    /**
     * Identifies a user at the Open ID Provider.
     */
    public static class ExternalSubject implements ExternalClaim {

        private final String iss;
        private final String sub;

        public ExternalSubject(String iss, String sub) {
            this.iss = iss;
            this.sub = sub;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ExternalSubject)) {
                return false;
            }
            var other = (ExternalSubject) obj;
            return Objects.equals(iss, other.iss)
                    && Objects.equals(sub, other.sub);
        }

        @Override
        public int hashCode() {
            return Objects.hash(iss, sub);
        }

        @Override
        public String toString() {
            return "[sub=" + sub + "]";
        }
    }

    /**
     * Identifies a user at the Open ID Provider.
     */
    public static class ExternalSession implements ExternalClaim {

        private final String iss;
        private final String sid;

        public ExternalSession(String iss, String sid) {
            this.iss = iss;
            this.sid = sid;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ExternalSession)) {
                return false;
            }
            var other = (ExternalSession) obj;
            return Objects.equals(iss, other.iss)
                    && Objects.equals(sid, other.sid);
        }

        @Override
        public int hashCode() {
            return Objects.hash(iss, sid);
        }

        @Override
        public String toString() {
            return "[sid=" + sid + "]";
        }
    }
}
