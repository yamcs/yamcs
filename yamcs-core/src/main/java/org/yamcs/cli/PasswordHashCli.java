package org.yamcs.cli;

import java.io.Console;
import java.util.Arrays;

import org.yamcs.security.PBKDF2PasswordHasher;
import org.yamcs.security.PasswordHasher;

import com.beust.jcommander.Parameters;

/**
 * Generates password hashes for use in users.yaml
 */
@Parameters(commandDescription = "Generate password hash for use in users.yaml")
public class PasswordHashCli extends Command {

    public PasswordHashCli(YamcsAdminCli yamcsCli) {
        super("password-hash", yamcsCli);
    }

    @Override
    void execute() throws Exception {
        console.println("Enter password: ");
        char[] newPassword = console.readPassword(false);
        console.println("Confirm password: ");
        char[] confirmedPassword = console.readPassword(false);

        if (!Arrays.equals(newPassword, confirmedPassword)) {
            console.println("Password confirmation does not match\n");
            exit(-1);
        }

        PasswordHasher hasher = new PBKDF2PasswordHasher();
        console.println(hasher.createHash(confirmedPassword));
        console.println("\n");
    }
}
