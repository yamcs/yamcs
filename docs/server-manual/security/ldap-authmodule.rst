LDAP AuthModule
===============

The LDAP AuthModule supports authentication and authorization of users via the LDAP protocol.

This module adds privileges to users through the use of roles: a user has specific roles, and some role is required for a specific privilege. All the user, role and privilege definitions are looked up in the LDAP database. Yamcs reads only LDAP objects of type ``groupOfNames``.

The algorithm used to build the list of user privileges is as follows:

* From the path configured by ``rolePath`` find all the roles associated to the user. The roles defined in LDAP must contain references using the member attribute to objects ``member=uid=username`` from the ``userPath``.
* For each role found previously, do a search in the corresponding system, tc, tm packet or tm parameter path using the match ``member=cn=role_name``. The cn of the matching entries is used to build the list of privileges that the user has.

.. note::

  This class can be stacked with other AuthModules such that it is responsible for either authentication or authorization. In the case of authorization read-only access to the LDAP database is assumed.


Class Name
----------

:javadoc:`org.yamcs.security.LdapAuthModule`


Configuration Options
---------------------

host (string)
    **Required.** The LDAP host

userPath (string)
    **Required.** The path to user definitions

rolePath (string)
    The path to role definitions

systemPath (string)
    The path to system privileges

tmParameterPath (string)
    The path to ``ReadParameter`` object privileges

tmPacketPath (string)
    The path to ``ReadPacket`` object privileges

tcPath (string)
    The path to ``Command`` object privileges
