package org.yamcs.cli;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.rocksdb.RocksDB;
import org.yamcs.http.api.IamApi;
import org.yamcs.protobuf.Mdb.SignificanceInfo.SignificanceLevelType;
import org.yamcs.protobuf.UserInfo;
import org.yamcs.security.Directory;
import org.yamcs.security.User;
import org.yamcs.security.protobuf.Clearance;
import org.yamcs.utils.TimeEncoding;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.google.protobuf.util.JsonFormat;

/**
 * Generates password hashes for use in users.yaml
 */
@Parameters(commandDescription = "User operations")
public class UsersCli extends Command {

    public UsersCli(YamcsAdminCli yamcsCli) {
        super("users", yamcsCli);
        addSubCommand(new AddRole());
        addSubCommand(new CheckPassword());
        addSubCommand(new CreateUser());
        addSubCommand(new DeleteUser());
        addSubCommand(new DescribeUser());
        addSubCommand(new ListUsers());
        addSubCommand(new RemoveIdentity());
        addSubCommand(new RemoveRole());
        addSubCommand(new ResetPassword());
        addSubCommand(new UpdateUser());
        TimeEncoding.setUp();
    }

    @Parameters(commandDescription = "Add a role to a user")
    private class AddRole extends Command {

        @Parameter(description = "The name of the user.")
        private List<String> username;

        @Parameter(names = "--role", required = true, description = "Role to be added.")
        private String role;

        AddRole() {
            super("add-role", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username.get(0) + "'");
                exit(-1);
            }

            user.addRole(role, false);
            directory.updateUserProperties(user);
        }
    }

    @Parameters(commandDescription = "Remove an identity from a user")
    private class RemoveIdentity extends Command {

        @Parameter(description = "The name of the user.")
        private List<String> username;

        @Parameter(names = "--identity", required = true, description = "Identity to be removed.")
        private String identity;

        RemoveIdentity() {
            super("remove-identity", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username.get(0) + "'");
                exit(-1);
            }

            user.deleteIdentity(identity);
            directory.updateUserProperties(user);
        }
    }

    @Parameters(commandDescription = "Remove a role from a user")
    private class RemoveRole extends Command {

        @Parameter(description = "The name of the user.")
        private List<String> username;

        @Parameter(names = "--role", required = true, description = "Role to be removed.")
        private String role;

        RemoveRole() {
            super("remove-role", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username.get(0) + "'");
                exit(-1);
            }

            user.deleteRole(role);
            directory.updateUserProperties(user);
        }
    }

    @Parameters(commandDescription = "Update a user")
    private class UpdateUser extends Command {

        @Parameter(description = "The name of the user.")
        private List<String> username;

        @Parameter(names = "--display-name", description = "Displayed name of the user.")
        private String displayName;

        @Parameter(names = "--email", description = "User email.")
        private String email;

        @Parameter(names = "--active", arity = 1, description = "Activate this user.")
        private Boolean active;

        @Parameter(names = "--superuser", arity = 1, description = "Grant superuser privileges")
        private Boolean superuser;

        @Parameter(names = "--clearance", description = "Clearance level of the user", converter = SignificanceLevelConverter.class)
        private SignificanceLevelType clearance;

        UpdateUser() {
            super("update", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username.get(0) + "'");
                exit(-1);
            }

            if (displayName != null) {
                user.setDisplayName(displayName);
            }
            if (email != null) {
                user.setEmail(email);
            }
            if (active != null) {
                user.setActive(active);
            }
            if (superuser != null) {
                user.setSuperuser(superuser);
            }
            if (clearance != null) {
                user.setClearance(Clearance.newBuilder()
                        .setLevel(clearance.name())
                        .setIssueTime(TimeEncoding.toProtobufTimestamp(TimeEncoding.getWallclockTime()))
                        .build());
            }

            directory.updateUserProperties(user);
        }
    }

    @Parameters(commandDescription = "Create a new user")
    private class CreateUser extends Command {

        @Parameter(description = "The name of the new user.")
        private List<String> username;

        @Parameter(names = "--email", description = "User email.")
        private String email;

        @Parameter(names = "--display-name", description = "Displayed name of the user.")
        private String displayName;

        @Parameter(names = "--inactive", description = "Add this flag to prevent Yamcs from activating the user.")
        private boolean inactive;

        @Parameter(names = "--superuser", description = "Add this flag to grant the user superuser privileges.")
        private boolean superuser;

        @Parameter(names = "--no-password", description = "Add this flag to indicate that this user should not have a password. This will also bypass the password prompt.")
        private boolean noPassword;

        @Parameter(names = "--clearance", description = "Clearance level of the user", converter = SignificanceLevelConverter.class)
        private SignificanceLevelType clearance;

        CreateUser() {
            super("create", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user != null) {
                console.println("user already exists: '" + username.get(0) + "'");
                exit(-1);
            }

            user = new User(username.get(0), null);
            user.setDisplayName(displayName);
            user.setEmail(email);
            user.setSuperuser(superuser);

            if (clearance != null) {
                user.setClearance(Clearance.newBuilder()
                        .setLevel(clearance.name())
                        .setIssueTime(TimeEncoding.toProtobufTimestamp(TimeEncoding.getWallclockTime()))
                        .build());
            }

            char[] password = null;
            if (!noPassword) {
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
            }

            if (!inactive) {
                user.confirm();
            }

            directory.addUser(user);
            if (password != null) {
                directory.changePassword(user, password);
            }
        }
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

            switch (getFormat()) {
            case JSON:
                List<UserInfo> users = directory.getUsers().stream()
                        .map(user -> IamApi.toUserInfo(user, true, directory))
                        .collect(Collectors.toList());
                console.println(printJsonArray(users));
                break;
            default:
                TableStringBuilder b = new TableStringBuilder("username", "display name", "email", "active",
                        "superuser");
                directory.getUsers().forEach(user -> {
                    b.addLine(user.getName(), user.getDisplayName(), user.getEmail(), user.isActive(),
                            user.isSuperuser());
                });
                console.println(b.toString());
            }
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
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username + "'");
                exit(-1);
            }

            switch (getFormat()) {
            case JSON:
                UserInfo userinfo = IamApi.toUserInfo(user, true, directory);
                console.println(JsonFormat.printer().print(userinfo));
                break;
            default:
                TableStringBuilder b = new TableStringBuilder(2);
                b.addLine("id:", user.getId());
                b.addLine("username:", user.getName());
                b.addLine("display name:", user.getDisplayName());
                b.addLine("email:", user.getEmail());
                b.addLine("active:", user.isActive());
                b.addLine("superuser:", user.isSuperuser());
                b.addLine("roles:", String.join(", ", user.getRoles()));
                if (user.getClearance() != null) {
                    b.addLine("clearance:", user.getClearance().getLevel());
                }
                b.addLine("external:", user.isExternallyManaged());
                b.addLine("created:", printInstant(user.getCreationTime()));
                b.addLine("confirmed:", printInstant(user.getConfirmationTime()));
                b.addLine("last login:", printInstant(user.getLastLoginTime()));
                console.println(b.toString());
            }
        }

        private String printInstant(long instant) {
            if (instant == TimeEncoding.INVALID_INSTANT) {
                return "";
            } else {
                return TimeEncoding.toString(instant);
            }

        }
    }

    @Parameters(commandDescription = "Delete user")
    private class DeleteUser extends Command {

        @Parameter()
        private List<String> username;

        DeleteUser() {
            super("delete", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username + "'");
                exit(-1);
            }

            directory.deleteUser(user);
        }
    }

    @Parameters(commandDescription = "Reset a user's password")
    private class ResetPassword extends Command {

        @Parameter()
        private List<String> username;

        ResetPassword() {
            super("reset-password", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username.get(0) + "'");
                exit(-1);
            }
            if (user.isExternallyManaged()) {
                console.println("credentials of user '" + username.get(0) + "' are not managed by Yamcs");
                exit(-1);
            }

            char[] newPassword;
            String newPasswordString = System.getenv("YAMCSADMIN_PASSWORD");
            if (newPasswordString == null) {
                console.print("Enter new password: ");
                newPassword = console.readPassword(false);
                console.print("Confirm new password: ");
                char[] confirmedPassword = console.readPassword(false);

                if (!Arrays.equals(newPassword, confirmedPassword)) {
                    console.println("Password confirmation does not match\n");
                    exit(-1);
                }
            } else {
                newPassword = newPasswordString.trim().toCharArray();
            }

            directory.changePassword(user, newPassword);
            console.println("Password updated successfully");
        }
    }

    @Parameters(commandDescription = "Check a user's password")
    private class CheckPassword extends Command {

        @Parameter()
        private List<String> username;

        CheckPassword() {
            super("check-password", UsersCli.this);
        }

        @Override
        void execute() throws Exception {
            RocksDB.loadLibrary();
            Directory directory = new Directory();

            if (username == null) {
                console.println("username not specified");
                exit(-1);
            }

            User user = directory.getUser(username.get(0));
            if (user == null) {
                console.println("invalid user '" + username.get(0) + "'");
                exit(-1);
            }
            if (user.isExternallyManaged()) {
                console.println("credentials of user '" + username.get(0) + "' are not managed by Yamcs");
                exit(-1);
            }

            char[] password;
            String passwordString = System.getenv("YAMCSADMIN_PASSWORD");
            if (passwordString == null) {
                console.print("Enter password: ");
                password = console.readPassword(false);
            } else {
                password = passwordString.trim().toCharArray();
            }

            if (directory.validateUserPassword(user.getName(), password)) {
                console.println("Password correct");
            } else {
                console.println("Password incorrect");
                exit(-1);
            }
        }
    }

    // Keep public, required by JCommander
    public static class SignificanceLevelConverter implements IStringConverter<SignificanceLevelType> {

        @Override
        public SignificanceLevelType convert(String value) {
            try {
                return SignificanceLevelType.valueOf(value.toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new ParameterException(
                        "Unknown value for --clearance. Possible values: "
                                + Arrays.asList(SignificanceLevelType.values()));
            }
        }
    }
}
