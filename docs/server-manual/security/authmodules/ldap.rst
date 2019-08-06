LDAP AuthModule
===============

The LDAP AuthModule supports authentication of users via the LDAP protocol.

Class Name
----------

:javadoc:`org.yamcs.security.LdapAuthModule`


Configuration Options
---------------------

host (string)
    **Required.** The LDAP host

userFormat (string)
    **Required.** The format of the principal used for binding. This must include a placeholder ``%s`` that will be substituted by the username of each login attempt.
    
    Example: ``uid=%s,ou=people,dc=example,dc=com`` or ``%s@upnSuffix``

port (integer)
    The LDAP port. Default: 389 for unencrypted connections, otherwise 636.

tls (boolean)
    If ``true`` the LDAP connection will be encrypted. Default: ``false``
