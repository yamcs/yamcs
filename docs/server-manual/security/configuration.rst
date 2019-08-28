Configuration
=============

The security system is configured in the file ``etc/security.yaml``. Example:

.. code-block:: yaml

    authModules:
      - class: org.yamcs.security.DirectoryModule

This requires that all login attempts are validated against the internal user directory of Yamcs.

These options are supported:

authModules (list of maps)
  List of AuthModules that participate in the login process. Each AuthModule may support custom configuration options which can be defined under the ``args`` key.

blockUnknownUsers (boolean)
    If you need tight control over who can access Yamcs, you can activate this option. Successful login attempts from users that were not yet known by Yamcs will be blocked by default. A privileged user may unblock them. The typical use case is when Yamcs uses an external identity provider that allows more users than really should be allowed access to Yamcs.

    Default: false

guest (map)
  Overrides the user properties of the guest user. Note that the guest user is only enabled if there are no authModules configured.
