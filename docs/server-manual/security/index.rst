Security
========

Yamcs includes a security subsystem which allows authenticating and authorizing users. Authentication is the act of identifying the user, whereas authorization involves determining what privileges this user has.

If security is disabled (the default if unconfigured), Yamcs will not require authentication and associate all API access with an imaginary `guest` user with superuser rights.

If security is enabled, users may be assigned privileges that determine what actions they can perform. Yamcs distinguishes between system privileges and object privileges.

.. note::
    Starting with Yamcs v5.13.6, Yamcs will no longer automatically create an ``admin`` user.

    If you are thinking about enabling security, you can yourself choose whether you want to create an internal user with superuser rights in the Admin Area. Or alternatively, such a user could come from an external identity provider through one of the AuthModules.

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    configuration
    system-privileges
    object-privileges
    superuser
    authmodules/index
