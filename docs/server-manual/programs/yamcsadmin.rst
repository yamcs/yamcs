yamcsadmin
==========

.. program:: yamcsadmin

**NAME**

    yamcsadmin - Tool for local Yamcs administration


**SYNOPSIS**

    ``yamcsadmin [--etc-dir DIR] COMMAND``


**OPTIONS**

    .. option:: --log LEVEL

       Level of verbosity. From 0 (off) to 5 (all). Default: 2.

    .. option:: --etc-dir DIR

        Override default Yamcs configuration directory.
    
    .. option:: --data-dir DIR

        Override default Yamcs data directory.

    .. option:: -h, --help

        Show usage.

    .. option:: -v, --version

        Print version information and quit.


**COMMANDS**

    :doc:`backup <yamcsadmin_backup>`
        Perform and restore backups
    :doc:`confcheck <yamcsadmin_confcheck>`
        Check Yamcs configuration
    :doc:`mdb <yamcsadmin_mdb>`
        Provides MDB information
    :doc:`parchive <yamcsadmin_parchive>`
        Parameter Archive operations
    :doc:`password-hash <yamcsadmin_password-hash>`
        Generate password hash for use in users.yaml
    :doc:`rocksdb <yamcsadmin_rocksdb>`
        Provides low-level RocksDB data operations
    :doc:`users <yamcsadmin_users>`
        User operations


.. toctree::
    :hidden:

    backup <yamcsadmin_backup>
    confcheck <yamcsadmin_confcheck>
    mdb <yamcsadmin_mdb>
    parchive <yamcsadmin_parchive>
    password-hash <yamcsadmin_password-hash>
    rocksdb <yamcsadmin_rocksdb>
    users <yamcsadmin_users>
