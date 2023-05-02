Configuration
=============

The security system is configured in the file :file:`etc/security.yaml`. Example:

.. code-block:: yaml

    enabled: true
    authModules:
      - class: org.yamcs.security.LdapAuthModule
        args:
           ...

This requires that all login attempts are validated against an external LDAP server.

These options are supported:

authModules (list of maps)
  List of AuthModules that participate in the login process. Each AuthModule may support custom configuration options which can be defined under the ``args`` key. If empty only the internal Yamcs directory is used as a source of users and roles.

blockUnknownUsers (boolean)
    Use this if you need fine control over who can access Yamcs. Successful login attempts from users that were not yet known by Yamcs will be blocked by default. A privileged user may unblock them. The typical use case is when Yamcs uses an external identity provider that allows more users than really should be allowed access to Yamcs.

    Default: false

enabled (boolean)
    Control whether authentication is enforced.
    
    Default: ``true`` if :file:`etc/security.yaml` is present, ``false`` otherwise.

guest (map)
    Overrides the user properties of the guest user. This user is used for all access when authentication is not being enforced.


.. rubric:: Roles

Roles are configured in the :file:`etc/roles.yaml`. This file defines which privileges belong to which roles. Example:

.. code-block:: yaml

    Operator:
      ReadParameter: [".*"]
      WriteParameter: []
      ReadPacket: [".*"]
      Command: [".*"]
      CommandHistory: [".*"]
      System:
        - ControlProcessor
        - ModifyCommandHistory
        - ControlCommandQueue
        - GetMissionDatabase
        - ControlAlarms
        - ControlArchiving

This example specifies one role ``Operator``. It also demonstrates the use of regular expressions to grant a set of object privileges.

System privileges must be defined under the key ``System``. System privileges may not use regular expressions.
