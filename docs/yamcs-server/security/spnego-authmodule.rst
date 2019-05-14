SPNEGO AuthModule
=================

This AuthModule supports authentication of users via an external Kerberos server. It does not support authorization and must therefore be stacked together with another AuthModule.


Class Name
----------

:javadoc:`org.yamcs.security.SpnegoAuthModule`


Configuration Options
---------------------

krbRealm (string)
    Accept only users from this realm.

stripRealm (boolean)
    | Whether to strip the realm from the username (e.g. ``user@REALM`` becomes just ``user``). Use this only when ``krbRealm`` is also set.
    | Default: ``false``.

krb5.conf (string)
    Absolute path to the applicable ``krb5.conf`` file.

jaas.conf (string)
    Absolute path to the applicable ``jaas.conf`` file.

The `jaas.conf` file must contain login modules called ``UserAuth`` and ``Yamcs``. Details are beyond the scope of this manual.
