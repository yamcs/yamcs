yamcsadmin confcheck
====================

**NAME**

    yamcsadmin confcheck - Check the configuration files of Yamcs


**SYNOPSIS**

    ``yamcsadmin confcheck [--no-etc] [DIR]``


**POSITIONAL ARGUMENTS**

    .. program:: yamcsadmin confcheck

    .. option:: [DIR]

        Use this directory in preference for loading configuration files. If ``--no-etc`` is specified, all configuration files will be loaded from this directory. Note that the data directory (yamcs.yaml dataDir) will be changed before starting the services, otherwise there will be RocksDB LOCK errors if a yamcs server is running.


**OPTIONS**

    .. program:: yamcsadmin confcheck

    .. option:: --no-etc

        Do not use any file from the default Yamcs etc directory. If this is specified, the argument ``[DIR]`` becomes mandatory.
