Get Table Info
==============

Get info on a Yamcs table::

    GET /api/archive/:instance/tables/:name

.. note::

    This is low-level API for those cases where access to an internal key/value table of Yamcs is wanted. It is recommended to use other API operations for any of the default built-in tables.


.. rubric:: Example
.. code-block:: json

    {
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
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message TableInfo {
      optional string name = 1;
      repeated ColumnInfo keyColumn = 2;
      repeated ColumnInfo valueColumn = 3;
    }
