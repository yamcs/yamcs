yamcsadmin backup restore
=========================

.. program:: yamcsadmin backup restore

**NAME**

    yamcsadmin backup restore - Restore a backup


**SYNOPSIS**

    ``yamcsadmin backup restore --backup-dir DIR --restore-dir DIR [ID]``


**DECRIPTION**

    Note that backups can only be restored when Yamcs is not running.


**POSITIONAL ARGUMENTS**

    .. option:: ID

        Backup ID. If unspecified this defaults to the last backup.


**OPTIONS**

    .. option:: --backup-dir DIR

        Directory containg backups.

    .. option:: --restore-dir DIR

        Directory where to restore the backup.
