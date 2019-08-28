LDAP AuthModule
===============

The LDAP AuthModule supports authentication of users via the LDAP protocol.

It first searches for the distinguished name that matches a submitted username, and then attempts a bind using the submitted password.

This module can also be chained to the :doc:`kerberos` or :doc:`spnego` modules in order to add user attributes to a user that logged in via Kerberos or Kerberos SPNEGO.

Class Name
----------

:javadoc:`org.yamcs.security.LdapAuthModule`


Configuration Options
---------------------

host (string)
    **Required.** The LDAP host

userBase (string)
    **Required.** The search base for users.
    
    Example: ``ou=people,dc=example,dc=com``

port (integer)
    The LDAP port. Default: 389 for unencrypted connections, otherwise 636.

tls (boolean)
    If ``true`` the LDAP connection will be encrypted. Default: ``false``

user (string)
    The bind DN that Yamcs should use to search user properties. If unspecified Yamcs will attempt to do an anonymous bind. On many LDAP installations an anonymous bind does not give sufficient access to user information.

password (string)
    The password matching the bind DN.

attributes (map)
    Configure which LDAP attributes are to be considered. If unset, Yamcs uses defaults that work out of the box with many LDAP installations.


Attributes sub-configuration
^^^^^^^^^^^^^^^^^^^^^^^^^^^^

name (string)
    The name of the account name attribute. This is used to search a DN within the ``userBase`` as well as to map to the Yamcs account name. Default: ``uid``.

email (string or string[])
    The name of the email attribute. If multiples are defined, they are tried in order. Default: ``[mail, email, userPrincipalName]``.

displayName (string or string[])
    The name of the display name attribute. If multiples are defined, they are tried in order. Default: ``cn``.
