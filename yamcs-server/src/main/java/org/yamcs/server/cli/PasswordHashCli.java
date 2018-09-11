package org.yamcs.server.cli;

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

    public PasswordHashCli(YamcsCtlCli yamcsCli) {
        super("password-hash", yamcsCli);
    }

    @Override
    void execute() throws Exception {
        Console console = System.console();
        console.printf("Enter password: ");
        char[] newPassword = console.readPassword();
        console.printf("Confirm password: ");
        char[] confirmedPassword = console.readPassword();

        if (!Arrays.equals(newPassword, confirmedPassword)) {
            console.printf("Password confirmation does not match\n");
            System.exit(-1);
        }

        PasswordHasher hasher = new PBKDF2PasswordHasher();
        console.printf(hasher.createHash(confirmedPassword));
        console.printf("\n");
    }
}
