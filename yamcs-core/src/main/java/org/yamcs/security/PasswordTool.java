package org.yamcs.security;

import java.io.Console;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

/**
 * TODO move to yamcs-ctl
 */
public class PasswordTool {

    public static void main(String[] args) throws NoSuchAlgorithmException, InvalidKeySpecException {
        Console console = System.console();
        if (console == null) {
            System.out.println("Couldn't get Console instance");
            System.exit(0);
        }

        console.printf("Enter new password: ");
        char[] newPassword = console.readPassword();
        console.printf("Confirm new password:");
        char[] confirmedPassword = console.readPassword();

        if (!Arrays.equals(newPassword, confirmedPassword)) {
            console.printf("Password confirmation does not match\n");
            System.exit(-1);
        }

        PasswordHasher hasher = new PBKDF2PasswordHasher();
        console.printf("Hashed with " + hasher.getClass() + ".\n");
        console.printf("Format is: iterations:hash:salt.\n");
        console.printf(hasher.createHash(confirmedPassword));
        console.printf("\n");
    }
}
