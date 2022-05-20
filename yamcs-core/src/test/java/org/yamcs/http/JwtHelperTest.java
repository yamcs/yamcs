package org.yamcs.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.http.auth.JwtHelper;
import org.yamcs.http.auth.JwtHelper.JwtDecodeException;
import org.yamcs.utils.TimeEncoding;

import com.google.gson.JsonObject;

public class JwtHelperTest {

    @BeforeAll
    public static void beforeClass() {
        TimeEncoding.setUp();
    }

    @Test
    public void testUnsigned() throws JwtDecodeException {
        String unsignedToken = JwtHelper.generateUnsignedToken("Yamcs", "someUser", 1000);

        JsonObject claims = JwtHelper.decodeUnverified(unsignedToken);
        assertEquals("Yamcs", claims.get("iss").getAsString());
        assertEquals("someUser", claims.get("sub").getAsString());
        assertEquals(1000L, claims.get("exp").getAsLong() - claims.get("iat").getAsLong());
    }

    @Test
    public void testHS256() throws InvalidKeyException, NoSuchAlgorithmException, JwtDecodeException {
        byte[] secret = "secret".getBytes();

        String signedToken = JwtHelper.generateHS256Token("Yamcs", "someUser", secret, 1000);

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
