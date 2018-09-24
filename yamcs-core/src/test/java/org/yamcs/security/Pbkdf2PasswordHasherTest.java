package org.yamcs.security;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import org.junit.Test;

/**
 * Created by msc on 07/05/15.
 */
public class Pbkdf2PasswordHasherTest {
    @Test
    public void hash_validate_ok() throws InvalidKeySpecException, NoSuchAlgorithmException {
        PBKDF2PasswordHasher hasher = new PBKDF2PasswordHasher();
        String password = "testtest";
        String hash = hasher.createHash(password.toCharArray());
        String secondHash = hasher.createHash(password.toCharArray());
        assertNotEquals(hash, secondHash);

        String wrongPassword = "wrong";
        assertFalse("Wrong password should not be accepted",
                (hasher.validatePassword(wrongPassword.toCharArray(), hash)));

        assertTrue("Good password should be accepted", hasher.validatePassword(password.toCharArray(), hash));
    }
}
