yamcsadmin
==========

.. program:: yamcsadmin

Synopsis
--------

.. rst-class:: synopsis

    | *yamcsadmin* [--etc-dir <*DIR*>] <*COMMAND*> [<*ARGS*>]


Options
-------

.. option:: --log <LEVEL>

    Level of verbosity. From 0 (off) to 5 (all). Default: 2.

.. option:: --etc-dir <DIR>

    Override default Yamcs configuration directory.

.. option:: --data-dir <DIR>

    Override default Yamcs data directory.

.. option:: -h, --help

    Show usage.

.. option:: -v, --version

    Print version information and quit.


Commands
--------

:doc:`backup <yamcsadmin_backup>`
    Perform and restore backups. See :manpage:`yamcsadmin-backup(1)`.
:doc:`confcheck <yamcsadmin_confcheck>`
    Check Yamcs configuration. See :manpage:`yamcsadmin-confcheck(1)`.
:doc:`mdb <yamcsadmin_mdb>`
    Provides MDB information. See :manpage:`yamcsadmin-mdb(1)`.
:doc:`password-hash <yamcsadmin_password-hash>`
    Generate password hash for use in :file:`etc/users.yaml`.
    See :manpage:`yamcsadmin-password-hash(1)`.
:doc:`rocksdb <yamcsadmin_rocksdb>`
    Provides low-level RocksDB data operations.
    See :manpage:`yamcsadmin-rocksdb(1)`.
:doc:`users <yamcsadmin_users>`
    User operations. See :manpage:`yamcsadmin-users(1)`.


.. only:: latex or json or html

    .. Purpose of "only" is to hide toctree content from the man builder

    .. toctree::
        :hidden:

        backup <yamcsadmin_backup>
        confcheck <yamcsadmin_confcheck>
        mdb <yamcsadmin_mdb>
        password-hash <yamcsadmin_password-hash>
        rocksdb <yamcsadmin_rocksdb>
        users <yamcsadmin_users>
