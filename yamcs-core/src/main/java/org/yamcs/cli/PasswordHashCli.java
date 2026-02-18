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
            jc.getConsole().println("Enter password: ");
            password = jc.getConsole().readPassword(false);
            jc.getConsole().println("Confirm password: ");
            char[] confirmedPassword = jc.getConsole().readPassword(false);

            if (!Arrays.equals(password, confirmedPassword)) {
                jc.getConsole().println("Password confirmation does not match\n");
                exit(-1);
            }
        } else {
            password = passwordString.trim().toCharArray();
        }

        PasswordHasher hasher = new PBKDF2PasswordHasher();
        jc.getConsole().println(hasher.createHash(password));
        jc.getConsole().println("\n");
    }
}
