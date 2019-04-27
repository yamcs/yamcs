List Databases
==============

List all RocksDB databases:

    GET /api/archive/:instance/rocksdb/list


.. rubric:: Response

The response is a list of directory names where the RocksDB databases are stored:

.. code-block:: text

    /storage/yamcs-data/yops/events-histo
    /storage/yamcs-data/yops/ParameterArchive
    /storage/yamcs-data/yops/pp-histo
    /storage/yamcs-data/yops/cmdhist-histo
    /storage/yamcs-data/yops/2016/pp
    /storage/yamcs-data/yops/tm-histo
