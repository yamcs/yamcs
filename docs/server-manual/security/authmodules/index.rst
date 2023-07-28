AuthModules
===========

.. toctree::
    :maxdepth: 1

    ldap
    yaml
    kerberos
    remote-user
    ip-address
    spnego
    openid


The security subsystem is modular by design and allows combining different AuthModules together. This allows for scenarios where for example you want to authenticate via LDAP, but determine privileges via YAML files.

The default set of AuthModules include:

:doc:`ldap`
    Authenticates against an LDAP directory. Also capable of mapping LDAP groups to Yamcs roles.
:doc:`yaml`
    Reads Yaml files to verify the credentials of the user, or assign privileges.
:doc:`kerberos`
    Supports authenticating against a Kerberos server.
:doc:`remote-user`
    Supports authentication based on a custom HTTP header.
:doc:`ip-address`
    Supports authentication based on the remote IP address.
:doc:`spnego`
    Supports authenticating against a Kerberos server using Single Sign On from a web context.
:doc:`openid`
    Supports authenticating against an OpenID Connect server.

AuthModules have an order. When a login attempt is made, AuthModules are iterated a first time in this order. Each AuthModule is asked if it can authenticate with the provided credentials. The first matching AuthModule contributes the user principal. A second iteration is done to then contribute privileges to the identified user. During both iterations, AuthModules reserve the right to halt the global login process for any reason.

Some AuthModules are only useful for specific flows. For example OpenID Connect (which in a nutshell redirects to an external login form) would need to be accompanied with other AuthModules in case not all target clients are browser-based.
