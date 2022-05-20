package org.yamcs.cli;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.yamcs.security.PBKDF2PasswordHasher;
import org.yamcs.security.PasswordHasher;

public class PasswordHashTest extends AbstractCliTest {

    @Test
    public void testPasswordNotMatching() throws Exception {
        mconsole.setPassword("pass1".toCharArray(), "pass2".toCharArray());
        int exitStatus = runMain("--etc-dir", "src/test/resources/YamcsServer", "password-hash");
        assertEquals(-1, exitStatus);
        assertTrue(mconsole.output().contains("Password confirmation does not match"));
    }

    @Test
    public void testOK() {
        char[] pass = "pass-word".toCharArray();
        mconsole.setPassword(pass, pass);
        assertEquals(0, runMain("--etc-dir", "src/test/resources/YamcsServer", "password-hash"));
        PasswordHasher hasher = new PBKDF2PasswordHasher();

        String out = mconsole.output();
        assertTrue(hasher.validatePassword(pass, out.split("\\n")[2]));
    }
}
