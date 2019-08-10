package org.yamcs.cli;

import java.io.Console;
import java.util.Arrays;
import java.util.List;

import org.rocksdb.RocksDB;
import org.yamcs.security.Directory;
import org.yamcs.security.User;
import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Generates password hashes for use in users.yaml
 */
@Parameters(commandDescription = "User operations")
public class UsersCli extends Command {

    public UsersCli(YamcsAdminCli yamcsCli) {
        super("users", yamcsCli);
        addSubCommand(new ListUsers());
        addSubCommand(new DescribeUser());
        addSubCommand(new ResetUserPassword());
        TimeEncoding.setUp();
    }

    @Parameters(commandDescription = "List users")
    private class ListUsers extends Command {

        ListUsers() {
            super("list", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            TableStringBuilder b = new TableStringBuilder("username", "display name", "email", "active", "superuser");
            directory.getUsers().forEach(user -> {
                b.addLine(user.getName(), user.getDisplayName(), user.getEmail(), user.isActive(), user.isSuperuser());
            });
            console.println(b.toString());
        }
    }

    @Parameters(commandDescription = "Describe user details")
    private class DescribeUser extends Command {

        @Parameter()
        private List<String> username;

        DescribeUser() {
            super("describe", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username + "'");
                System.exit(-1);
            }

            TableStringBuilder b = new TableStringBuilder(2);
            b.addLine("id:", user.getId());
            b.addLine("username:", user.getName());
            b.addLine("display name:", user.getDisplayName());
            b.addLine("email:", user.getEmail());
            b.addLine("active:", user.isActive());
            b.addLine("superuser:", user.isSuperuser());
            b.addLine("external:", user.isExternallyManaged());
            b.addLine("created:", printInstant(user.getCreationTime()));
            b.addLine("confirmed:", printInstant(user.getConfirmationTime()));
            b.addLine("last login:", printInstant(user.getLastLoginTime()));
            console.println(b.toString());
        }

        private String printInstant(long instant) {
            if (instant == TimeEncoding.INVALID_INSTANT) {
                return "";
            } else {
                return TimeEncoding.toString(instant);
            }

        }
    }

    @Parameters(commandDescription = "Reset a user's password")
    private class ResetUserPassword extends Command {

        @Parameter()
        private List<String> username;

        ResetUserPassword() {
            super("reset-password", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username.get(0) + "'");
                System.exit(-1);
            }
            if (user.isExternallyManaged()) {
                console.println("credentials of user '" + username.get(0) + "' are not managed by Yamcs");
                System.exit(-1);
            }

            Console console = System.console();
            console.printf("Enter new password: ");
            char[] newPassword = console.readPassword();
            console.printf("Confirm new password: ");
            char[] confirmedPassword = console.readPassword();

            if (!Arrays.equals(newPassword, confirmedPassword)) {
                console.printf("Password confirmation does not match\n");
                System.exit(-1);
            }

            directory.changePassword(user, newPassword);
        }
    }
}
