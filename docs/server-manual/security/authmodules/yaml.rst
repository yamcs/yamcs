YAML AuthModule
===============

This AuthModule supports authentication and authorization of users via YAML files available directly in the Yamcs configuration folder.

Class Name
----------

:javadoc:`org.yamcs.security.YamlAuthModule`


Configuration Options
---------------------

hasher (string)
    Hasher class that can be used to verify if a password is correct without actually storing the password. When omitted, passwords in :file:`etc/users.yaml` should be defined in clear text. Possible values are:

    * :javadoc:`org.yamcs.security.PBKDF2PasswordHasher`

required (boolean)
    When set to ``true`` the YAML AuthModule will veto the login process if it does not know the user. This may be of interest in situations where the YAML AuthModule does not authenticate the user, yet still some control is required via configuration files over which users can login. Default is ``false``.

The YAML AuthModule reads further configuration from a YAML file: :file:`etc/users.yaml`.


users.yaml
----------

This file defines users, passwords and user roles.

.. code-block:: yaml

    admin:
      password: somepassword
      superuser: true

    someuser:
      displayName: Some User
      password: somepassword
      roles: [ Operator ]

The ``password`` key may be omitted if the YAML AuthModule is not used for authentication.

If you do use YAML AuthModule for authentication, consider hashing the passwords for better security. Password hashes can be obtained via the command line:

.. code-block:: text

    yamcsadmin password-hash

This command prompts for the password and outputs a randomly salted PBKDF2 hash. This output can be assigned to the ``password`` key, replacing the clear password.
