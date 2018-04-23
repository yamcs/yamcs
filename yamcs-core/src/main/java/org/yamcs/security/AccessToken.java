package org.yamcs.security;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.yamcs.security.JWT.JWTDecodeException;

import com.google.gson.JsonObject;

/**
 * Identifies a user that was authenticated via a JWT bearer token
 */
public class AccessToken implements AuthenticationToken {

    private JsonObject claims;

    public AccessToken(String jwt, String secretKey) throws JWTDecodeException {
        try {
            this.claims = JWT.decode(jwt, secretKey);
        } catch (InvalidKeyException | NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public Object getPrincipal() {
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
        return getPrincipal().toString();
    }
}
