package org.yamcs.cli;

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
        char[] password;
        String passwordString = System.getenv("YAMCSADMIN_PASSWORD");
        if (passwordString == null) {
            console.println("Enter password: ");
            password = console.readPassword(false);
            console.println("Confirm password: ");
            char[] confirmedPassword = console.readPassword(false);

            if (!Arrays.equals(password, confirmedPassword)) {
                console.println("Password confirmation does not match\n");
                exit(-1);
            }
        } else {
            password = passwordString.trim().toCharArray();
        }

        PasswordHasher hasher = new PBKDF2PasswordHasher();
        console.println(hasher.createHash(password));
        console.println("\n");
    }
}
