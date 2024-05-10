System Privileges
=================

A system privilege is the right to perform a particular action or to perform an action on any object of a particular type.

ControlProcessor
    Allows to control any processor.

CreateInstances
    Allows to create instances.

ModifyCommandHistory
    Allows to modify command history.

ControlCommandClearances
    Allows to clear users for commanding.

ControlCommandQueue
    Allows to manage command queues.

CommandOptions
    Allows specifying command options (extra attributes in the command history, disable/modify verifiers, stream selection).

GetMissionDatabase
    Allows to read Mission Database definitions.

ChangeMissionDatabase
    Allows online changes to Mission Database definitions.

ReadAlarms
    Allows to read alarms.

ControlAlarms
    Allows to manage alarms.

ReadActivities
    Allows to read activities.

ControlActivities
    Allows to manage activities.

ControlArchiving
    Allows to manage archiving properties of Yamcs.

ReadLinks
    Allows to read link state.

ControlLinks
    Allows to control the lifecycle of any link.

ControlServices
    Allows to manage the lifecycle of services.

ManageParameterLists
    Allows to manage the definition of parameter lists.

ManageAnyBucket
    Provides full control over any :doc:`bucket <../data-management/buckets>` (including user buckets).

    A typical installation includes at least the buckets ``displays`` and ``stacks``.

ReadEvents
    Allows to read any event.

WriteEvents
    Allows to manually create events.

WriteTables
    Allows to manually add records to tables.

ReadTables
    Allows to read tables.

ReadTimeline
    Allows to view the timeline.

ControlTimeline
    Allows to modify the timeline.

ControlAccess
    Allows to control access (users, groups, roles, ...)

ReadSystemInfo
    Allows to view system information (:abbr:`OS ( Operating System)`, :abbr:`JVM (Java Virtual Machine)`, threads, replication, ...)

ControlFileTransfers
    Allows to create file transfers.

ReadFileTransfers
    Allows read access to file transfer information.


.. note::

    Yamcs plugins may support additional system privileges.

    For example, the yamcs-web plugin uses the following privilege to control access to the Admin Area: ``web.AccessAdminArea``
