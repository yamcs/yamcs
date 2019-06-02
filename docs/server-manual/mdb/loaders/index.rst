Loading TM/TC Definitions
=========================

.. toctree::
    :maxdepth: 1

    xtce
    sheet/index
    emptyNode

Yamcs constructs its Mission Database on server startup from a configurable tree of *loaders*. Each loader is responsible for a particular space system, and optionally its sub-space systems. It is not possible for one loader to add to adjacent space systems.

.. code-block:: yaml
    :caption: Configuration Example

    simulator:
      - type: "sheet"
        spec: "mdb/simulator-ccsds.xls"
        subLoaders:
          - type: "sheet"
            spec: "mdb/simulator-tmtc.xls"


Multiple different types of loaders may be combined in the loader tree to assemble the full mission database. Each loader can load definitions from any source as long as the definitions can be mapped into Yamcs internal database format, which is based on the XTCE constructs.

For start-up performance, the database is cached serialized on disk in the cache directory. The cached database is composed of two files, one storing the data itself and the other one storing the time when the cache file has been created. These files should be considered Yamcs internal and are subject to change.

.. note::

    Yamcs does not persist TM/TC definitions and therefore does not have any "import" functionality.
