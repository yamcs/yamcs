Loading TM/TC Definitions
=========================

.. toctree::
    :maxdepth: 1

    xtce
    sheet/index
    emptyNode

Yamcs constructs its Mission Database on server startup from a configurable tree of *loaders*. Each loader is responsible for a particular space system, and optionally its sub-space systems. It is not possible for one loader to add to adjacent space systems.

The tree of space systems (also called a *loader tree*) is typically defined in the instance configuration file :file:`etc/yamcs.{instance}.yaml` under the ``mdb`` section:

.. code-block:: yaml
    :caption: :file:`etc/yamcs.{instance}.yaml`

    mdb:
      - type: "sheet"
        spec: "mdb/simulator-ccsds.xls"
        subLoaders:
          - type: "sheet"
            spec: "mdb/simulator-tmtc.xls"

Alternatively, you can also define arbitrarily named configurations in a configuration file :file:`etc/mdb.yaml`, and then reference the configuration by that name from the instance configuration file using the key ``mdbSpec``:


.. code-block:: yaml
    :caption: :file:`etc/mdb.yaml`

    simulator:
      - type: "sheet"
        spec: "mdb/simulator-ccsds.xls"
        subLoaders:
          - type: "sheet"
            spec: "mdb/simulator-tmtc.xls"

.. code-block:: yaml
    :caption: :file:`etc/yamcs.{instance}.yaml`

    mdbSpec: simulator


Multiple different types of loaders may be combined in the loader tree to assemble the full mission database. Each loader can load definitions from any source as long as the definitions can be mapped into Yamcs internal database format, which is based on the XTCE constructs.

For start-up performance, the database is cached serialized on disk in the cache directory. The cached database is composed of two files, one storing the data itself and the other one storing the time when the cache file has been created. These files should be considered Yamcs internal and are subject to change.

A database loader (for example the XTCE loader) is able to load multiple space systems which will all be added as siblings. In this case, the subLoaders option cannot be anymore specified (because otherwise it would not be clear to which of the loaded space systems the children will be added).


.. note::

    Yamcs does not persist TM/TC definitions and therefore does not have any "import" functionality.
