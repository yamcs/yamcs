yamcsadmin rocksdb compact
==========================

.. program:: yamcsadmin rocksdb compact

**NAME**

    yamcsadmin rocksdb compact - Compact rocksdb database


**SYNOPSIS**

    ``yamcsadmin rocksdb compact [--dbDir DIR] [--sizeMB SIZE]``


**OPTIONS**

    .. option:: --dbDir DIR

        Database directory.

    .. option:: --sizeMB SIZE

        Target size of each SST file in MB (default is 256 MB).
