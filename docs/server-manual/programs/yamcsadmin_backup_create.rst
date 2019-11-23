yamcsadmin backup create
========================

.. program:: yamcsadmin backup create

**NAME**

    yamcsadmin backup create - Create a new backup


**SYNOPSIS**

    .. code-block:: text
    
        yamcsadmin backup create --backup-dir DIR [--data-dir DIR]
                                 [--url HOST:PORT] TABLESPACE


**DESCRIPTION**

    This subcommand allows to create either a hot or a cold backup of a Yamcs tablespace. For cold backups, specify the ``--data-dir`` property, for hot backups specify the ``--host`` property.


**POSITIONAL ARGUMENTS**

    .. option:: TABLESPACE

        The name of the tablespace to backup.


**OPTIONS**

    .. option:: --backup-dir DIR

        Target directory containing backups. This directory is automatically created if it does not exist prior to taking the backup.

    .. option:: --data-dir DIR

        Path to a Yamcs data directory. This must be specified when performing a cold backup.

    .. option:: --host HOST:PORT

        Perform a hot backup. This allows to take a consistent backup while Yamcs is running. Backup are currently triggered using a JMX operation.
