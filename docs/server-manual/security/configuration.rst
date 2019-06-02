Configuration
=============

The security system is configured in the file ``etc/security.yaml``. Example from a typical deployment:

.. code-block:: yaml

    enabled: true

    # Implicit user when security is _not_ enabled
    unauthenticatedUser:
      username: admin
      superuser: true

    authModules:
      - class: org.yamcs.security.YamlAuthModule
        config:
          hasher: org.yamcs.security.PBKDF2PasswordHasher

These options are supported:

enabled
  Whether security is enabled. If ``false`` then Yamcs will not require users to login and will assume that everybody shares a single account defined under the ``unauthenticatedUser`` property.

unauthenticatedUser
  Configures the user details of the default user. This property is only considered when ``enabled`` is set to ``false``.

authModules
  List of AuthModules that particpate in the login process. Each AuthModule may support custom configuration options which can be defined under the ``config`` key.
