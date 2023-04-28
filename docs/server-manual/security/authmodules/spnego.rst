SPNEGO AuthModule
=================

This AuthModule supports Single Sign On authentication of users via SPNEGO. This is usually stacked together with the :doc:`kerberos` module in case the single sign on does not work, or in case Yamcs is accessed from a non-web context.


Class Name
----------

:javadoc:`org.yamcs.security.SpnegoAuthModule`


Configuration Options
---------------------

principal (string)
    | **Required.** Kerberos Service Principal of the HTTP service that matches the external address of Yamcs.
    | This should be in the format ``HTTP/<host>.<domain>@<realm>``

keytab (string)
    | **Required.** Path to the keytab file matching the principal.

stripRealm (boolean)
    | Whether to strip the realm from the username (e.g. ``user@<realm>`` becomes just ``user``).
    | Default: ``true``.

This module reads Kerberos configuration from the Kerberos system configuration file. This is usually available at :file:`/etc/krb5.conf`. If you need to override this location, you have to set a system property at :abbr:`JVM (Java Virtual Machine)` level:

.. code-block:: text

   -Djava.security.krb5.conf=/my/custom/krb5.conf
