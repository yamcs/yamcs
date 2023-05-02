Spreadsheet Loader
==================

The spreadsheet loader loads TM/TC definitions from an Excel spreadsheet. The spreadsheet structure must follow a specific structure. The advantage of this loader is that the Excel files are very convenient to modify with any spreadsheet program. It is recommended to start from an existing example and replace its content as required.

The Excel file must be in Excel 97-2003 Format (``.xls``). ``.xlsx`` is not supported.

The loader is configured in :file:`etc/mdb.yaml` or in the instance configuration by specifying the type as ``sheet``, and providing the location of the XML file.

.. code-block:: yaml

    - type: "sheet"
      args:
        file: "mdb/BogusSAT.xls"

The following notation is also accepted for historical reasons:

.. code-block:: yaml

   - type: "sheet"
     spec: "mdb/BogusSAT.xls"


.. rubric:: Configuration Options

file (string)
   **Required.** The filename to be loaded.

enableXtceNameRestrictions (boolean)
   If true, names must only use characters, digits, underscores or dashes. Default: ``true``


.. rubric:: Sheets

The spreadsheet may contain any sheets, however only the following names are considered, and further detailed in their respective sections.

.. hlist::
   :columns: 2

   * :doc:`General <general>` (required)
   * :doc:`ChangeLog <changelog>`
   * :doc:`DataTypes <data-types>`
   * :doc:`Parameters <parameters>`
   * :doc:`DerivedParameters <derived-parameters>`
   * :doc:`LocalParameters <local-parameters>`
   * :doc:`Containers <containers>`
   * :doc:`Algorithms <algorithms>`
   * :doc:`Alarms <alarms>`
   * :doc:`Commands <commands>`
   * :doc:`CommandOptions <command-options>`
   * :doc:`CommandVerification <command-verification>`
   * :doc:`Calibration <calibration>`


.. rubric:: Multiple Space Systems

A spreadsheet file describes one space system. Multiple spreadsheets can be combined in a space system tree as described in :doc:`../index`.

Alternatively, Yamcs also allows to describe a tree of space systems in a single spreadsheet file with the following rules:

* All sheets that do not have a prefix, contain data for the main space system whose name is defined in the :doc:`general`.
* To define data in subsystems, a sheet can be named like ``SYSTEM1|SYSTEM2|Containers``. This definition will create a ``SYSTEM1`` as part of the main space system and a child ``SYSTEM2`` of ``SYSTEM1``. Then the containers will be loaded in ``SYSTEM2``.

The spreadsheet loader scans and creates the subsystem hierarchy and then it loads the data inside the systems traversing the hierarchy in a depth-first order.


.. rubric:: Number Base

Numeric values can be entered as decimals or as hexadecimals (with prefix ``0x``)


.. rubric:: Referencing Parameter and Containers

Name references can be used to refer to items in other space systems. They look like UNIX-like directory access expressions, such as ``../a/b``.

The result of the lookup depends on the exact tree configuration in :file:`etc/mdb.yaml`


.. rubric:: Comments

Rows that begin with the symbol '#' in their first cell are ignored.


.. toctree::
    :maxdepth: 1
    :hidden:

    general
    changelog
    data-types
    parameters
    derived-parameters
    local-parameters
    containers
    algorithms
    alarms
    commands
    command-options
    command-verification
    calibration
