package org.yamcs.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.junit.jupiter.api.Test;

public class Pbkdf2PasswordHasherTest {

    @Test
    public void hash_validate_ok() throws InvalidKeySpecException, NoSuchAlgorithmException {
        PBKDF2PasswordHasher hasher = new PBKDF2PasswordHasher();
        String password = "testtest";
        String hash = hasher.createHash(password.toCharArray());
        String secondHash = hasher.createHash(password.toCharArray());
        assertNotEquals(hash, secondHash);

        String wrongPassword = "wrong";
        assertFalse(hasher.validatePassword(wrongPassword.toCharArray(), hash),
                "Wrong password should not be accepted");

        assertTrue(hasher.validatePassword(password.toCharArray(), hash), "Good password should be accepted");
    }
}
