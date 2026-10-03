package org.yamcs.security;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.yamcs.security.JwksVerifier.JwtVerificationException;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class JwksVerifierTest {

    private static final String EVENT = "http://schemas.openid.net/event/backchannel-logout";

    private static KeyPair rsaKeys;
    private static KeyPair ecKeys;
    private static JsonObject jwks;

    @BeforeAll
    public static void setup() throws Exception {
        var rsaGen = KeyPairGenerator.getInstance("RSA");
        rsaGen.initialize(2048);
        rsaKeys = rsaGen.generateKeyPair();

        var ecGen = KeyPairGenerator.getInstance("EC");
        ecGen.initialize(new ECGenParameterSpec("secp256r1"));
        ecKeys = ecGen.generateKeyPair();

        var rsaJwk = new JsonObject();
        rsaJwk.addProperty("kty", "RSA");
        rsaJwk.addProperty("kid", "rsa1");
        rsaJwk.addProperty("use", "sig");
        rsaJwk.addProperty("n", b64(((RSAPublicKey) rsaKeys.getPublic()).getModulus()));
        rsaJwk.addProperty("e", b64(((RSAPublicKey) rsaKeys.getPublic()).getPublicExponent()));

        var ecPoint = ((ECPublicKey) ecKeys.getPublic()).getW();
        var ecJwk = new JsonObject();
        ecJwk.addProperty("kty", "EC");
        ecJwk.addProperty("kid", "ec1");
        ecJwk.addProperty("crv", "P-256");
        ecJwk.addProperty("x", b64(ecPoint.getAffineX()));
        ecJwk.addProperty("y", b64(ecPoint.getAffineY()));

        var keys = new JsonArray();
        keys.add(rsaJwk);
        keys.add(ecJwk);
        jwks = new JsonObject();
        jwks.add("keys", keys);
    }

    @Test
    public void testValidSignatures() throws Exception {
        var verifier = new JwksVerifier(() -> jwks);
        var claims = logoutClaims();

        for (var alg : new String[] { "RS256", "PS256" }) {
            var token = sign(alg, "rsa1", claims, rsaKeys.getPrivate());
            assertEquals("victim", verifier.verify(token).get("sub").getAsString());
        }
        var token = sign("ES256", "ec1", claims, ecKeys.getPrivate());
        assertEquals("victim", verifier.verify(token).get("sub").getAsString());
    }

    @Test
    public void testRejectedTokens() throws Exception {
        var verifier = new JwksVerifier(() -> jwks);
        var claims = logoutClaims();

        // Unsigned (the advisory's PoC)
        var unsigned = b64(header("none", null)) + "." + b64(claims.toString()) + ".";
        assertThrows(JwtVerificationException.class, () -> verifier.verify(unsigned));

        // Symmetric algorithm
        var hmac = Mac.getInstance("HmacSHA256");
        hmac.init(new SecretKeySpec("secret".getBytes(), "HmacSHA256"));
        var hsInput = b64(header("HS256", null)) + "." + b64(claims.toString());
        var hs256 = hsInput + "." + b64(hmac.doFinal(hsInput.getBytes(UTF_8)));
        assertThrows(JwtVerificationException.class, () -> verifier.verify(hs256));

        // Tampered payload
        var valid = sign("RS256", "rsa1", claims, rsaKeys.getPrivate());
        var parts = valid.split("\\.");
        var other = logoutClaims();
        other.addProperty("sub", "someone-else");
        var tampered = parts[0] + "." + b64(other.toString()) + "." + parts[2];
        assertThrows(JwtVerificationException.class, () -> verifier.verify(tampered));

        // Signed by another key
        var otherGen = KeyPairGenerator.getInstance("RSA");
        otherGen.initialize(2048);
        var otherKeys = otherGen.generateKeyPair();
        var wrongKey = sign("RS256", "rsa1", claims, otherKeys.getPrivate());
        assertThrows(JwtVerificationException.class, () -> verifier.verify(wrongKey));

        // Key of the wrong type for the algorithm
        var wrongType = sign("RS256", "ec1", claims, rsaKeys.getPrivate());
        assertThrows(JwtVerificationException.class, () -> verifier.verify(wrongType));
    }

    @Test
    public void testUnknownKeyDoesNotRefetchImmediately() throws Exception {
        var loads = new AtomicInteger();
        var verifier = new JwksVerifier(() -> {
            loads.incrementAndGet();
            return jwks;
        });

        var claims = logoutClaims();
        verifier.verify(sign("RS256", "rsa1", claims, rsaKeys.getPrivate()));
        assertEquals(1, loads.get());

        var unknownKid = sign("RS256", "unknown", claims, rsaKeys.getPrivate());
        assertThrows(JwtVerificationException.class, () -> verifier.verify(unknownKid));
        assertThrows(JwtVerificationException.class, () -> verifier.verify(unknownKid));
        assertEquals(1, loads.get());
    }

    @Test
    public void testLogoutClaims() throws Exception {
        var now = System.currentTimeMillis() / 1000;
        OpenIDAuthModule.validateLogoutClaims(logoutClaims(), "https://idp", "yamcs", now);

        var noEvent = logoutClaims();
        noEvent.remove("events");
        assertInvalid(noEvent, now);

        var withNonce = logoutClaims();
        withNonce.addProperty("nonce", "abc");
        assertInvalid(withNonce, now);

        var otherAudience = logoutClaims();
        otherAudience.addProperty("aud", "other-client");
        assertInvalid(otherAudience, now);

        var expired = logoutClaims();
        expired.addProperty("exp", now - 3600);
        assertInvalid(expired, now);

        var noSubject = logoutClaims();
        noSubject.remove("sub");
        assertInvalid(noSubject, now);

        var otherIssuer = logoutClaims();
        otherIssuer.addProperty("iss", "https://evil");
        assertInvalid(otherIssuer, now);
    }

    private static void assertInvalid(JsonObject claims, long now) {
        assertThrows(JwtVerificationException.class,
                () -> OpenIDAuthModule.validateLogoutClaims(claims, "https://idp", "yamcs", now));
    }

    private static JsonObject logoutClaims() {
        var now = System.currentTimeMillis() / 1000;
        var claims = new JsonObject();
        claims.addProperty("iss", "https://idp");
        claims.addProperty("aud", "yamcs");
        claims.addProperty("iat", now);
        claims.addProperty("exp", now + 120);
        claims.addProperty("jti", "1");
        claims.addProperty("sub", "victim");
        var events = new JsonObject();
        events.add(EVENT, new JsonObject());
        claims.add("events", events);
        return claims;
    }

    private static String header(String alg, String kid) {
        var header = new JsonObject();
        header.addProperty("alg", alg);
        if (kid != null) {
            header.addProperty("kid", kid);
        }
        return header.toString();
    }

    private static String sign(String alg, String kid, JsonObject claims, PrivateKey key) throws Exception {
        var input = b64(header(alg, kid)) + "." + b64(claims.toString());
        Signature signature;
        switch (alg) {
        case "RS256":
            signature = Signature.getInstance("SHA256withRSA");
            break;
        case "PS256":
            signature = Signature.getInstance("RSASSA-PSS");
            signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
            break;
        case "ES256":
            signature = Signature.getInstance("SHA256withECDSAinP1363Format");
            break;
        default:
            throw new IllegalArgumentException(alg);
        }
        signature.initSign(key);
        signature.update(input.getBytes(UTF_8));
        return input + "." + b64(signature.sign());
    }

    private static String b64(String s) {
        return b64(s.getBytes(UTF_8));
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Unsigned big-endian, as used in JWKs
    private static String b64(BigInteger value) {
        var bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return b64(bytes);
    }
}
