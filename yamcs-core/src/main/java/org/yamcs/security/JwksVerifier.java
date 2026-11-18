package org.yamcs.security;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.math.BigInteger;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPublicKeySpec;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.yamcs.logging.Log;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Verifies the signature of JSON Web Tokens issued by an OpenID server, using the public keys that the server
 * publishes as a JSON Web Key Set (JWKS).
 * <p>
 * Only asymmetric algorithms are supported. Tokens with {@code alg} {@code none} or an HMAC algorithm are rejected.
 */
public class JwksVerifier {

    private static final Log log = new Log(JwksVerifier.class);

    // Limits how often the key set is fetched, for example when tokens refer to an unknown key
    private static final long REFRESH_INTERVAL = 30_000;

    private final JwksLoader loader;

    private List<Jwk> keys;
    private long lastFetch;

    public JwksVerifier(JwksLoader loader) {
        this.loader = loader;
    }

    /**
     * Verifies the signature of the provided token, and returns its claims.
     */
    public JsonObject verify(String token) throws JwtVerificationException {
        var parts = token.split("\\.", -1);
        if (parts.length != 3) {
            throw new JwtVerificationException("Token does not consist of three parts");
        }

        var header = decodeJson(parts[0], "header");
        if (header.has("crit")) {
            throw new JwtVerificationException("Unsupported critical header parameters");
        }
        var alg = getString(header, "alg");
        var signatureAlgorithm = SignatureAlgorithm.forName(alg);
        if (signatureAlgorithm == null) {
            throw new JwtVerificationException("Unsupported algorithm: " + alg);
        }

        var key = findKey(getString(header, "kid"), signatureAlgorithm);

        byte[] signature;
        try {
            signature = Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new JwtVerificationException("Invalid signature encoding");
        }

        var signingInput = (parts[0] + "." + parts[1]).getBytes(US_ASCII);
        if (!signatureAlgorithm.verify(key, signingInput, signature)) {
            throw new JwtVerificationException("Invalid signature");
        }

        return decodeJson(parts[1], "payload");
    }

    private synchronized PublicKey findKey(String kid, SignatureAlgorithm alg) throws JwtVerificationException {
        var key = (keys != null) ? selectKey(kid, alg) : null;
        if (key == null && System.currentTimeMillis() - lastFetch >= REFRESH_INTERVAL) {
            refresh();
            key = (keys != null) ? selectKey(kid, alg) : null;
        }
        if (key == null) {
            if (keys == null) {
                throw new JwtVerificationException("Signing keys of the OpenID server are not available");
            }
            throw new JwtVerificationException("No matching signing key"
                    + (kid != null ? " for kid '" + kid + "'" : ""));
        }
        return key;
    }

    private void refresh() {
        lastFetch = System.currentTimeMillis();
        try {
            keys = parseKeySet(loader.load());
            log.debug("Loaded {} signing keys", keys.size());
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to retrieve signing keys of the OpenID server: {}", e.toString());
        }
    }

    private PublicKey selectKey(String kid, SignatureAlgorithm alg) {
        var candidates = new ArrayList<Jwk>();
        for (var jwk : keys) {
            if (jwk.kty().equals(alg.kty)
                    && (jwk.alg() == null || jwk.alg().equals(alg.name()))
                    && (kid == null || kid.equals(jwk.kid()))
                    && alg.isCompatible(jwk.key())) {
                candidates.add(jwk);
            }
        }

        if (kid != null) {
            return candidates.isEmpty() ? null : candidates.get(0).key();
        } else {
            // Without key id, the choice must not be ambiguous
            return candidates.size() == 1 ? candidates.get(0).key() : null;
        }
    }

    static List<Jwk> parseKeySet(JsonObject jwks) {
        var result = new ArrayList<Jwk>();
        var keysArray = jwks.getAsJsonArray("keys");
        if (keysArray == null) {
            throw new JsonParseException("Missing 'keys' in key set");
        }
        for (JsonElement el : keysArray) {
            if (!el.isJsonObject()) {
                continue;
            }
            var obj = el.getAsJsonObject();
            var use = getString(obj, "use");
            if (use != null && !use.equals("sig")) {
                continue;
            }
            var kty = getString(obj, "kty");
            try {
                PublicKey key;
                if ("RSA".equals(kty)) {
                    key = toRsaKey(obj);
                } else if ("EC".equals(kty)) {
                    key = toEcKey(obj);
                } else {
                    continue;
                }
                result.add(new Jwk(getString(obj, "kid"), kty, getString(obj, "alg"), key));
            } catch (GeneralSecurityException | IllegalArgumentException | NullPointerException e) {
                log.warn("Ignoring invalid key '{}': {}", getString(obj, "kid"), e.toString());
            }
        }
        return result;
    }

    private static PublicKey toRsaKey(JsonObject jwk) throws GeneralSecurityException {
        var n = new BigInteger(1, Base64.getUrlDecoder().decode(getString(jwk, "n")));
        var e = new BigInteger(1, Base64.getUrlDecoder().decode(getString(jwk, "e")));
        return KeyFactory.getInstance("RSA").generatePublic(new RSAPublicKeySpec(n, e));
    }

    private static PublicKey toEcKey(JsonObject jwk) throws GeneralSecurityException {
        var crv = getString(jwk, "crv");
        String curveName;
        if ("P-256".equals(crv)) {
            curveName = "secp256r1";
        } else if ("P-384".equals(crv)) {
            curveName = "secp384r1";
        } else if ("P-521".equals(crv)) {
            curveName = "secp521r1";
        } else {
            throw new GeneralSecurityException("Unsupported curve: " + crv);
        }
        var params = AlgorithmParameters.getInstance("EC");
        params.init(new ECGenParameterSpec(curveName));
        var spec = params.getParameterSpec(ECParameterSpec.class);

        var x = new BigInteger(1, Base64.getUrlDecoder().decode(getString(jwk, "x")));
        var y = new BigInteger(1, Base64.getUrlDecoder().decode(getString(jwk, "y")));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(new ECPoint(x, y), spec));
    }

    private static JsonObject decodeJson(String part, String what) throws JwtVerificationException {
        try {
            var json = new String(Base64.getUrlDecoder().decode(part), UTF_8);
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (IllegalArgumentException | IllegalStateException | JsonParseException e) {
            throw new JwtVerificationException("Invalid token " + what);
        }
    }

    private static String getString(JsonObject obj, String key) {
        var el = obj.get(key);
        return (el != null && el.isJsonPrimitive()) ? el.getAsString() : null;
    }

    /**
     * Loads a JSON Web Key Set.
     */
    @FunctionalInterface
    public interface JwksLoader {
        JsonObject load() throws IOException;
    }

    record Jwk(String kid, String kty, String alg, PublicKey key) {
    }

    @SuppressWarnings("serial")
    public static class JwtVerificationException extends Exception {
        public JwtVerificationException(String message) {
            super(message);
        }
    }

    private enum SignatureAlgorithm {
        RS256("RSA", "SHA256withRSA", 0),
        RS384("RSA", "SHA384withRSA", 0),
        RS512("RSA", "SHA512withRSA", 0),
        PS256("RSA", "SHA-256", 0),
        PS384("RSA", "SHA-384", 0),
        PS512("RSA", "SHA-512", 0),
        ES256("EC", "SHA256withECDSAinP1363Format", 256),
        ES384("EC", "SHA384withECDSAinP1363Format", 384),
        ES512("EC", "SHA512withECDSAinP1363Format", 521);

        final String kty;
        final String jcaName; // For PS*, the digest name
        final int curveSize; // For ES*, the required curve

        SignatureAlgorithm(String kty, String jcaName, int curveSize) {
            this.kty = kty;
            this.jcaName = jcaName;
            this.curveSize = curveSize;
        }

        static SignatureAlgorithm forName(String alg) {
            for (var value : values()) {
                if (value.name().equals(alg)) {
                    return value;
                }
            }
            return null;
        }

        boolean isCompatible(PublicKey key) {
            if (curveSize == 0) {
                return true;
            }
            var fieldSize = ((ECPublicKey) key).getParams().getCurve().getField().getFieldSize();
            return fieldSize == curveSize;
        }

        boolean verify(PublicKey key, byte[] signingInput, byte[] signature) throws JwtVerificationException {
            try {
                Signature verifier;
                if (name().startsWith("PS")) {
                    verifier = Signature.getInstance("RSASSA-PSS");
                    var digestLength = Integer.parseInt(name().substring(2)) / 8;
                    verifier.setParameter(new PSSParameterSpec(jcaName, "MGF1",
                            new MGF1ParameterSpec(jcaName), digestLength, 1));
                } else {
                    verifier = Signature.getInstance(jcaName);
                }
                verifier.initVerify(key);
                verifier.update(signingInput);
                return verifier.verify(signature);
            } catch (GeneralSecurityException e) {
                throw new JwtVerificationException("Signature verification failed: " + e.getMessage());
            }
        }
    }
}
