Kerberos AuthModule
===================

This AuthModule supports password-based authentication of users via an external Kerberos server.


Class Name
----------

:javadoc:`org.yamcs.security.KerberosAuthModule`


Configuration Options
---------------------

This module reads Kerberos configuration from the Kerberos system configuration file. This is usually available at :file:`/etc/krb5.conf`. If you need to override this location, you have to set a system property at :abbr:`JVM (Java Virtual Machine)` level:

    -Djava.security.krb5.conf=/my/custom/krb5.conf
