package org.yamcs.http.auth;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.yamcs.http.auth.JwtHelper.JwtDecodeException;

import com.google.gson.JsonObject;

/**
 * Identifies a user that was authenticated via a JWT bearer token
 */
public class JwtToken {

    private JsonObject claims;

    public JwtToken(String jwt, byte[] secretKey) throws JwtDecodeException {
        try {
            this.claims = JwtHelper.decode(jwt, secretKey);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    public String getSubject() {
        return claims.get("sub").getAsString();
    }

    public boolean isExpired() {
        if (!claims.has("exp")) {
            return false;
        }
        long expTimeInSeconds = claims.get("exp").getAsLong();
        return expTimeInSeconds < System.currentTimeMillis() / 1000;
    }

    @Override
    public String toString() {
        return getSubject();
    }
}
