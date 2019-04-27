Security
========

Yamcs includes a security subsystem which allows authenticating and authorizing users. Authentication is the act of identifying the user, whereas authorization involves determining what privileges this user has.

Once authorized, the user may be assigned one or more privileges that determine what actions the user can perform. Yamcs distinguishes between system privileges and object privileges.


System Privileges
-----------------

A system privilege is the right to perform a particular action or to perform an action on any object of a particular type.

ControlProcessor
    Allows to control any processor.
CreateInstances
    Allows to create instances.
ModifyCommandHistory
    Allows to modify command history.
ControlCommandQueue
    Allows to manage command queues.
Command
    Allows to issue any command.
GetMissionDatabase
    Allows to read the entire Mission Database.
ChangeMissionDatabase
    Allows online changes to the Mission Database.
ControlArchiving
    Allows to manage archiving properties of Yamcs.
ControlLinks
    Allows to control the lifecycle of any data link.
ControlServices
    Allows to manage the lifecycle of services.
ManageAnyBucket
    Provides full control over any bucket (including user buckets).
ReadEvents
    Allows to read any event.
WriteEvents
    Allows to manually post events.
WriteTables
    Allows to manually add records to tables.
ReadTables
    Allows to read tables.

.. note::

    Yamcs extensions may support additional system privileges.


Object Privileges
-----------------

An object privilege is the right to perform a particular action on an object. The object is assumed to be identifiable by a single string. The object may also be expressed as a regular expression, in which case Yamcs will perform pattern matching when doing authorization checks.

Command
    Allows to issue a particular command
CommandHistory
    Allows access to the command history of a particular command
InsertCommandQueue
    Allows to insert commands to a particular queue
ManageBucket
    Allow control over a specific bucket
ReadBucket
    Allow readonly access to a specific bucket
ReadPacket
    Allows to read a particular packet
ReadParameter
    Allows to read a particular parameter
Stream
    Allow to read and emit to a specific stream
WriteParameter
    Allows to set the value of a particular parameter

.. note::

    Yamcs extensions may support additional object privileges.


Superuser
---------

A user may have the attribute ``superuser``. Such a user is not subject to privilege checking. Any check of any kind will automatically pass. An example of such a user is the ``System`` user which is used internally by Yamcs on some actions that cannot be tied to a specific user. The ``superuser`` attribute may also be assigned to end users if the AuthModule supports it.


AuthModules
-----------

The security subsystem is modular by design and allows combining different AuthModules together. This allows for scenarios where for example you want to authenticate via LDAP, but determine privileges via YAML files.

The default set of AuthModules include:

:doc:`yaml-authmodule`
    Reads Yaml files to verify the credentials of the user, or assign privileges.
:doc:`ldap-authmodule`
    Attempts to bind to LDAP with the provided credentials. Also capable of reading privileges for the user.
:doc:`spnego-authmodule`
    Supports authenticating against a Kerberos server. This module includes extra support for Single-Sign-On via the Yamcs web interface.

AuthModules have an order. When a login attempt is made, AuthModules are iterated a first time in this order. Each AuthModule is asked if it can authenticate with the provided credentials. The first matching AuthModule contributes the user principal. A second iteration is done to then contribute privileges to the identified user. During both iterations, AuthModules reserve the right to halt the global login process for any reason.


Configuration
-------------

Example from a typical deployment:

.. code-block:: yaml
    :caption: security.yaml

    enabled: true

    # Implicit user when security is _not_ enabled
    unauthenticatedUser:
      username: admin
      superuser: true

    authModules:
      - class: org.yamcs.security.YamlAuthModule
        config:
          hasher: org.yamcs.security.PBKDF2PasswordHasher

These options are supported:

enabled
  Whether security is enabled. If ``false`` then Yamcs will not require users to login and will assume that everybody shares a single account defined under the ``unauthenticatedUser`` property.

unauthenticatedUser
  Configures the user details of the default user. This property is only considered when ``enabled`` is set to ``false``.

authModules
  List of AuthModules that particpate in the login process. Each AuthModule may support custom configuration options which can be defined under the ``config`` key.

.. toctree::
    :hidden:

    yaml-authmodule
    ldap-authmodule
    spnego-authmodule
