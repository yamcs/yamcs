Single User AuthModule
======================

This AuthModule supports authentication and authorization of a single user whose information is directly specified in the AuthModule configuration.


Class Name
----------

:javadoc:`org.yamcs.security.SingleUserAuthModule`


Configuration Options
---------------------

username (string)
    **Required.** Username of the authenticated user.

password (string)
    **Required.** Password for this user.

name (string)
    Display name of the user account.

email (string)
    Email address of the user account.

superuser (boolean)
    If ``true`` the account has superuser privileges. Superusers are not subject to permission checks. Default: ``false``.

privileges (map)
    Map of assigned privileges, where each entry is either:

    * An object privilege, with as value a list of patterns.
    * The special name ``System``, with as value a list of system privileges.

hasher (string)
    Hasher class that can be used to verify if a password is correct without actually storing the password. When omitted, passwords in :file:`etc/users.yaml` should be defined in clear text. Possible values are:

    * :javadoc:`org.yamcs.security.PBKDF2PasswordHasher`
