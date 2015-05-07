package org.yamcs.security;

import org.junit.Assert;
import org.junit.Test;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

/**
 * Created by msc on 07/05/15.
 */
public class PasswordHashTest {
    @Test
    public void hash_validate_ok() throws InvalidKeySpecException, NoSuchAlgorithmException {

                // Print out 10 hashes
            for(int i = 0; i < 10; i++)
                System.out.println(PasswordHash.createHash("p\r\nassw0Rd!"));

            // Test password validation
            boolean failure = false;
            System.out.println("Running tests...");
            for(int i = 0; i < 100; i++)
            {
                String password = ""+i;
                String hash = PasswordHash.createHash(password);
                String secondHash = PasswordHash.createHash(password);
                Assert.assertFalse("Two hashes should not be equal", hash.equals(secondHash));

                String wrongPassword = ""+(i+1);
                Assert.assertFalse("Wrong password should not be accepted", (PasswordHash.validatePassword(wrongPassword, hash)));

                Assert.assertTrue("Good password should be accepted", PasswordHash.validatePassword(password, hash));
            }
        }

}
