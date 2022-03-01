package org.yamcs.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final SecureRandom RNG = new SecureRandom();

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    private static final String PASSWORD_CHARS;
    static {
        String lower = "abcdefghijklmnopqrstuvwxyz";
        String upper = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String number = "0123456789";
        String misc = "!@#$%&*()_+-=[]?";
        PASSWORD_CHARS = lower + upper + number + misc;
    }

    /**
     * Generates a difficult to guess random key via SecureRandom using the HmacSHA1 algorithm
     */
    public static byte[] generateRandomSecretKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(HMAC_SHA1_ALGORITHM);
            keyGen.init(RNG);
            SecretKey secretKey = keyGen.generateKey();
            return secretKey.getEncoded();
        } catch (NoSuchAlgorithmException e) {
            // Should not happen. HmacSHA1 is available in any JDK
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Generates a random strong password.
     */
    public static String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int idx = RNG.nextInt(PASSWORD_CHARS.length());
            sb.append(PASSWORD_CHARS.charAt(idx));
        }
        return sb.toString();
    }

    /**
     * Calculates an hmac as specified in RFC2104.
     */
    public static byte[] calculateHmac(String data, byte[] secret) {
        return calculateHmac(data.getBytes(StandardCharsets.UTF_8), secret);
    }

    /**
     * Calculates an hmac as specified in RFC2104.
     */
    public static byte[] calculateHmac(byte[] data, byte[] secret) {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(secret, HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException e) {
            // Should not happen. HmacSHA1 is available in any JDK
            throw new UnsupportedOperationException(e);
        } catch (InvalidKeyException e) {
            // Should not happen. Key is specified in this method
            throw new UnsupportedOperationException(e);
        }
    }

    /**
     * Generates an OAuth 2.0 Proof Key for Code Exchange (PKCE) code challenge and verifier (RFC6736)
     * 
     * <pre>
     * code_challenge = BASE64URLENCODE(SHA256(ASCII(code_verifier)))
     * </pre>
     */
    public static PKCE generatePKCE() {
        byte[] codeVerifierBytes = new byte[32];
        RNG.nextBytes(codeVerifierBytes);
        PKCE pkce = new PKCE();
        pkce.codeVerifier = Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
        try {
            byte[] bytes = pkce.codeVerifier.getBytes(StandardCharsets.US_ASCII);
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(bytes, 0, bytes.length);
            byte[] codeChallengeBytes = messageDigest.digest();
            pkce.codeChallenge = Base64.getUrlEncoder().withoutPadding().encodeToString(codeChallengeBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException(e);
        }
        return pkce;
    }

    public static final class PKCE {
        public String codeChallenge;
        public String codeVerifier;
    }
}
