Security
========

.. toctree::
    :maxdepth: 1
    :caption: Table of Contents

    system-privileges
    object-privileges
    superuser
    authmodules/index
    configuration

Yamcs includes a security subsystem which allows authenticating and authorizing users. Authentication is the act of identifying the user, whereas authorization involves determining what privileges this user has.

Once authorized, the user may be assigned one or more privileges that determine what actions the user can perform. Yamcs distinguishes between system privileges and object privileges.
