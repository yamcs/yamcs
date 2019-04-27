YAML AuthModule
===============

This AuthModule supports authentication and authorization of users via YAML files available directly in the ``etc/`` folder.

Class Name
----------

:javadoc:`org.yamcs.security.YamlAuthModule`


Configuration Options
---------------------

hasher (string)
    Hasher class that can be used to verify if a password is correct without actually storing the password. When omitted, passwords in ``users.yaml`` should be defined in clear text. Possible values are:

    * :javadoc:`org.yamcs.security.PBKDF2PasswordHasher`

required (boolean)
    When set to ``true`` the YAML AuthModule will veto the login process if it does not know the user. This may be of interest in situations where the YAML AuthModule does not authenticate the user, yet still some control is required via configuration files over which users can login. Default is ``false``.

The YAML AuthModule reads further configuration from two additional YAML files: ``users.yaml`` and ``roles.yaml``.


users.yaml
----------

This file defines users, passwords and user roles. Note that Yamcs itself does not use roles, it is however used as a convenience in the YAML AuthModule to reduce the verbosity of user-specific privilege assignments.

.. code-block:: yaml
    :caption: users.yaml

    admin:
      password: somepassword
      superuser: true

    someuser:
      password: somepassword
      roles: [ Operator ]

The ``password`` key may be omitted if the YAML AuthModule is not used for authentication.

If you do use YAML AuthModule for authentication, consider hashing the passwords for better security. Password hashes can be obtained via the command line::

    yamcsadmin password-hash

This command prompts for the password and outputs a randomly salted PBKDF2 hash. This output can be assigned to the ``password`` key, replacing the clear password.


roles.yaml
----------

This file defines which privileges belong to which roles.

.. code-block:: yaml
    :caption: roles.yaml

    Operator:
      ReadParameter: [".*"]
      WriteParameter: []
      ReadPacket: [".*"]
      Command: [".*"]
      InsertCommandQueue: ["ops"]
      System:
        - ControlProcessor
        - ModifyCommandHistory
        - ControlCommandQueue
        - Command
        - GetMissionDatabase
        - ControlArchiving

This example specifies one role ``Operator``. It also demonstrates the use of regular expressions to grant a set of object privileges.

System privileges must be defined under the key ``System``. System privileges may not use regular expressions.

All keys are optional so the simplest role definition is simply:

.. code-block:: yaml
    :caption: roles.yaml

    EmptyRole:
