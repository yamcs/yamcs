package org.yamcs.security;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

import org.junit.Test;
import org.yamcs.security.JWT;
import org.yamcs.security.JWT.JWTDecodeException;
import org.yamcs.security.User;
import org.yamcs.security.UsernamePasswordToken;

import com.google.gson.JsonObject;

public class JWTTest {

    @Test()
    public void testUnsigned() throws JWTDecodeException {
        User testUser = new User(new UsernamePasswordToken("someUser", "pw"));
        String unsignedToken = JWT.generateUnsignedToken(testUser, 1000);

        JsonObject claims = JWT.decodeUnverified(unsignedToken);
        assertEquals("Yamcs", claims.get("iss").getAsString());
        assertEquals("someUser", claims.get("sub").getAsString());
        assertEquals(1000L, claims.get("exp").getAsLong());
    }

    @Test
    public void testHS256() throws InvalidKeyException, NoSuchAlgorithmException, JWTDecodeException {
        User testUser = new User(new UsernamePasswordToken("someUser", "pw"));
        String signedToken = JWT.generateHS256Token(testUser, "secret", 1000);

        JsonObject unverifiedClaims = JWT.decodeUnverified(signedToken);
        assertEquals("Yamcs", unverifiedClaims.get("iss").getAsString());
        assertEquals("someUser", unverifiedClaims.get("sub").getAsString());
        assertEquals(1000L, unverifiedClaims.get("exp").getAsLong());

        JsonObject verifiedClaims = JWT.decode(signedToken, "secret");
        assertEquals("Yamcs", verifiedClaims.get("iss").getAsString());
        assertEquals("someUser", verifiedClaims.get("sub").getAsString());
        assertEquals(1000L, verifiedClaims.get("exp").getAsLong());

        boolean throwsException = false;
        try {
            JWT.decode(signedToken, "wrong-secret");
        } catch (JWTDecodeException e) {
            throwsException = true;
        }
        assertTrue(throwsException);
    }
}
