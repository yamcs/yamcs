package org.yamcs.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;
import org.yamcs.security.User;
import org.yamcs.web.JwtHelper.JwtDecodeException;

import com.google.gson.JsonObject;

public class JwtHelperTest {

    @Test()
    public void testUnsigned() throws JwtDecodeException {
        User testUser = new User("someUser");
        String unsignedToken = JwtHelper.generateUnsignedToken(testUser, 1000);

        JsonObject claims = JwtHelper.decodeUnverified(unsignedToken);
        assertEquals("Yamcs", claims.get("iss").getAsString());
        assertEquals("someUser", claims.get("sub").getAsString());
        assertEquals(1000L, claims.get("exp").getAsLong() - claims.get("iat").getAsLong());
    }

    @Test
    public void testHS256() throws InvalidKeyException, NoSuchAlgorithmException, JwtDecodeException {
        byte[] secret = "secret".getBytes();

        User testUser = new User("someUser");
        String signedToken = JwtHelper.generateHS256Token(testUser, secret, 1000);

        JsonObject unverifiedClaims = JwtHelper.decodeUnverified(signedToken);
        assertEquals("Yamcs", unverifiedClaims.get("iss").getAsString());
        assertEquals("someUser", unverifiedClaims.get("sub").getAsString());
        assertEquals(1000L, unverifiedClaims.get("exp").getAsLong() - unverifiedClaims.get("iat").getAsLong());

        JsonObject verifiedClaims = JwtHelper.decode(signedToken, secret);
        assertEquals("Yamcs", verifiedClaims.get("iss").getAsString());
        assertEquals("someUser", verifiedClaims.get("sub").getAsString());
        assertEquals(1000L, verifiedClaims.get("exp").getAsLong() - verifiedClaims.get("iat").getAsLong());

        boolean throwsException = false;
        try {
            JwtHelper.decode(signedToken, "wrong-secret".getBytes());
        } catch (JwtDecodeException e) {
            throwsException = true;
        }
        assertTrue(throwsException);
    }
}
