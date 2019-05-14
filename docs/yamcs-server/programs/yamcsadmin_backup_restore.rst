yamcsadmin backup restore
=========================

.. program:: yamcsadmin backup restore

**NAME**

    yamcsadmin backup restore - Restore a backup


**SYNOPSIS**

    ``yamcsadmin backup restore [--backupDir DIR] [--backupId ID] [--restoreDir DIR]``


**DECRIPTION**

    Note that backups can only be restored when Yamcs is not running.


**OPTIONS**

    .. option:: --backupDir DIR

        Backup directory.

    .. option:: --backupId ID

        Backup ID. If not specified, defaults to the last backup.

    .. option:: --restoreDir DIR

        Directory where to restore the backup.
