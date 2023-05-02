IP Address AuthModule
=====================

This AuthModule supports the login of a single preconfigured user based on an authorized remote IP address. Currently, it can only be used for API requests, and not for accessing the Yamcs web interface.


Class Name
----------

:javadoc:`org.yamcs.security.IPAddressAuthModule`


Configuration Options
---------------------

address (string or list of strings)
    IPv4 or IPv6 address, or a range with CIDR mask.

    A list of addresses or ranges may be specified. The user is then accepted when any of the entries matches the incoming request.

username (string)
    Username of the authenticated user.

name (string)
    Display name of the user account.

email (string)
    Email address of the user account.

superuser (boolean)
    If ``true`` the account has superuser privileges. Superusers are not subject to permission checks.

privileges (map)
    Map of assigned privileges, where each entry is either:

    * An object privilege, with as value a list of patterns.
    * The special name ``System``, with as value a list of system privileges.


Example
-------

AuthModules are configured in the file :file:`etc/security.yaml`.

.. code-block:: yaml

    authModules:
      - class: org.yamcs.security.IPAddressAuthModule
        args:
          address: "127.0.0.1"
          username: ipv4_user

      - class: org.yamcs.security.IPAddressAuthModule
        args:
          address: "::1"
          username: ipv6_user

      - class: org.yamcs.security.IPAddressAuthModule
        args:
          address:
            - "192.168.0.0/16"
            - "127.0.0.1"
          username: testuser
