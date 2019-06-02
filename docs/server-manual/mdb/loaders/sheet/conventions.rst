Conventions
===========

.. rubric:: Multiple Space Systems

A tree of multiple space systems can be defined in a single Excel file.

To define the space system hierarchy, the convention is that all the sheets that do not have a prefix contain data for the main space system whose name is defined in the :doc:`general`.
To define data in subsystems, a syntax like ``SYSTEM1|SYSTEM2|Containers`` can be used. This definition will create a SYSTEM1 as part of the main space system and a child SYSTEM2 of SYSTEM1. Then the containers will be loaded in SYSTEM2.

The spreadsheet loader scans and creates the subsystem hierarchy and then it loads the data inside the systems traversing the hierarchy in a depht-first order.


.. rubric:: Number Base

Numeric values can be entered as decimals or as hexadecimals (with prefix ``0x``)


.. rubric:: Referencing Parameter and Containers

Each time a name reference is mentioned in the spreadsheet, the following rules apply:

* The reference can use UNIX like directory access expressions, such as ``../a/b``.
* If the name is not found as a qualified parameter, and the option enableAliasReferences is configured for the SpreadsheetLoader, the parameter is looked up through all the aliases of the parent systems.

The result of the lookup depends on the exact tree configuration in ``mdb.yaml``
