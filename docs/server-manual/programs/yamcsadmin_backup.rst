yamcsadmin backup
=================

.. program:: yamcsadmin backup

Synopsis
--------

.. rst-class:: synopsis

    | **yamcsadmin backup** create --backup-dir <*DIR*> [--data-dir <*DIR*>]
                             [--url <*HOST:PORT*>] <*TABLESPACE*>
    | **yamcsadmin backup** delete --backup-dir <*DIR*> <*ID*>...
    | **yamcsadmin backup** list --backup-dir <*DIR*>
    | **yamcsadmin backup** purge --backup-dir <*DIR*> --keep <*N*>
    | **yamcsadmin backup** restore --backup-dir <*DIR*> --restore-dir <*DIR*> [<*ID*>]


Description
-----------

Use :program:`yamcsadmin backup` when you want to save and restore Yamcs data.

Backups are performed at the level of a tablespace, which (unless otherwise configured) corresponds with an instance name. A special tablespace ``_global`` contains data that is not specific to an instance.

The backup directory is in binary format and can contain multiple restore points, one for each time the ``create`` command was used. Use the ``list`` command to see all restore points in a backup directory.


Commands
--------

.. describe:: create --backup-dir <DIR> [--data-dir <DIR>] [--url <HOST:PORT>] <TABLESPACE>

    Create either a hot or a cold backup of a Yamcs tablespace. For cold backups, specify the :option:`--data-dir` property, for hot backups specify the :option:`--host` property.

.. describe:: delete --backup-dir <DIR> <ID>...

    Delete one or more backups.

.. describe:: list --backup-dir <DIR>

    List the existing backups.

.. describe:: purge --backup-dir <DIR> --keep <N>

    Purge old backups.

.. describe:: restore --backup-dir <DIR> --restore-dir <DIR> [<ID>]

    Restore a backup by its ID.

    If unspecified ``<ID>`` defaults to the last backup.

    Note that backups can only be restored when Yamcs is not running.


Options
-------

.. option:: --backup-dir <DIR>

    Directory containing backups.

    When used with the ``create`` command, the directory is automatically created if it does not yet exist.

.. option:: --data-dir <DIR>

    This option is only valid for the ``create`` command.

    Path to a Yamcs data directory. This must be specified when performing a cold backup.

.. option:: --restore-dir <DIR>

    This option is only valid for the ``restore`` command.

    Directory where to restore the backup.

.. option:: --host <HOST:PORT>

    This option is only valid for the ``create`` command.

    Perform a hot backup. This allows to take a consistent backup while Yamcs is running. Backup are currently triggered using a JMX operation.

.. option:: --keep <N>

    This option is only valid for the ``purge`` command.

    The number of backups to keep.

.. option:: <ID>

   A unique identifier for a restore point. You can find existing identifiers using the ``list`` command.
