yamcsadmin
==========

.. program:: yamcsadmin

**NAME**

    yamcsadmin - Tool for local Yamcs administration


**SYNOPSIS**

    ``yamcsadmin [--etc-dir DIR] COMMAND``


**OPTIONS**

    .. option:: --etc-dir DIR

        Override default Yamcs configuration directory.

    .. option:: -h, --help

        Show usage.

    .. option:: -v, --version

        Print version information and quit.


**COMMANDS**

    :doc:`backup <yamcsadmin_backup>`
        Perform and restore backups
    :doc:`confcheck <yamcsadmin_confcheck>`
        Check the configuration files of Yamcs
    :doc:`parchive <yamcsadmin_parchive>`
        Parameter Archive operations
    :doc:`password-hash <yamcsadmin_password-hash>`
        Generate password hash for use in users.yaml
    :doc:`rocksdb <yamcsadmin_rocksdb>`
        Provides low-level RocksDB data operations
    :doc:`xtcedb <yamcsadmin_xtcedb>`
        Provides information about the XTCE database


.. toctree::
    :hidden:

    backup <yamcsadmin_backup>
    confcheck <yamcsadmin_confcheck>
    parchive <yamcsadmin_parchive>
    password-hash <yamcsadmin_password-hash>
    rocksdb <yamcsadmin_rocksdb>
    xtcedb <yamcsadmin_xtcedb>
