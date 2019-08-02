Configuration
=============

The security system is configured in the file ``etc/security.yaml``. Example:

.. code-block:: yaml

    authModules:
      - class: org.yamcs.security.DirectoryModule

This requires that all login attempts are validated against the internal user directory of Yamcs.

These options are supported:

authModules
  List of AuthModules that participate in the login process. Each AuthModule may support custom configuration options which can be defined under the ``config`` key.

guestAccess
  Overrides the user properties of the guest user. Note that the guest user is only enabled if there are no authModules configured.
