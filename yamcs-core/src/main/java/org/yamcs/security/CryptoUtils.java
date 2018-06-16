package org.yamcs.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

    private static final SecureRandom RNG = new SecureRandom();

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

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
}
