package org.yamcs.security;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

public interface PasswordHasher {

    String createHash(char[] password) throws NoSuchAlgorithmException, InvalidKeySpecException;

    /**
     * Validates a password using a hash.
     *
     * @param password
     *            the password to check
     * @param expectedHash
     *            the hash of the valid password
     * @return true if the password is correct, false if not
     */
    boolean validatePassword(char[] password, String expectedHash)
            throws NoSuchAlgorithmException, InvalidKeySpecException;
}
