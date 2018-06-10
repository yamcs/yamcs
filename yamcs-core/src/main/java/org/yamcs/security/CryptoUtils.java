package org.yamcs.security;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

public class CryptoUtils {

    private static final SecureRandom RNG = new SecureRandom();

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    /**
     * Generates a difficult to guess random key via SecureRandom using the HmacSHA1 algorithm and followed by Base64
     * encoding
     */
    public static String generateRandomKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(HMAC_SHA1_ALGORITHM);
            keyGen.init(RNG);
            SecretKey secretKey = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(secretKey.getEncoded());
        } catch (NoSuchAlgorithmException e) {
            // Should not happen. HmacSHA1 is available in any JDK
            throw new UnsupportedOperationException(e);
        }
    }
}
