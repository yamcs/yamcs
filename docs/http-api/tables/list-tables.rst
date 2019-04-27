List Tables
===========

List all tables for the given instance::

    GET /api/archive/:instance/tables

.. note::

    This is low-level API for those cases where access to the internal key/value tables of Yamcs is wanted. It is recommended to use other API operations for any of the default built-in tables.

.. note::

    The response will only include fixed columns of the table. Tuples may always add extra value columns.


.. rubric:: Example
.. code-block:: json

    {
      "table" : [ {
        "name" : "tm",
        "keyColumn" : [ {
          "name" : "gentime",
          "type" : "TIMESTAMP"
        }, {
          "name" : "seqNum",
          "type" : "INT"
        } ],
        "valueColumn" : [ {
          "name" : "rectime",
          "type" : "TIMESTAMP"
        }, {
          "name" : "packet",
          "type" : "BINARY"
        }, {
          "name" : "pname",
          "type" : "ENUM"
        } ]
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListTablesResponse {
      repeated archive.TableInfo table = 1;
    }
