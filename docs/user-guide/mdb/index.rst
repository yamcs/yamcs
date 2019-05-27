Mission Database
================

The Yamcs Mission Database is composed of a hierarchical structure, each node in the hierarchy being an XTCE SpaceSystem. Each SpaceSystem contains the following data:

* Telemetry
* Telecommands
* Parameters
* Algorithms

For faster access, the database is cached serialized on disk in the cache directory. The cached mission database is composed of two files, one storing the data itself and the other one storing the time when the cache file has been created. These files should be considered Yamcs internal and are subject to change.
 
Different loaders are possible for each node in the hiearchy. A loader can load a node and its subnodes (but cannot load two parallel nodes).

.. code-block:: yaml

    refmdb:
      - type: "sheet"
        spec: "mdb/refmdb-ccsds.xls"
        subLoaders:
          - type: "sheet"
            spec: "mdb/refmdb-subsys1.xls"

    simulator:
      - type: "sheet"
        spec: "mdb/simulator-ccsds.xls"
        subLoaders:
          - type: "sheet"
            spec: "mdb/simulator-tmtc.xls"

.. toctree::
    :maxdepth: 1

    spreadsheet-loader
    xtce-loader
    empty-node
