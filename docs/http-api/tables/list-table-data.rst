List Table Data
===============

List the most recent data of a Yamcs table::

    GET /api/archive/:instance/tables/:table/data

.. note::

    This is low-level API for those cases where access to an internal key/value table of Yamcs is wanted. It is recommended to use other API operations for any of the default built-in tables.


.. rubric:: Parameters

cols (array of strings)
    The columns to be included in the result. Both these notations are accepted:

    * ``?cols=rectime,gentime,pname``
    * ``?cols[]=rectime&cols[]=gentime&cols[]=pname``

    If unspecified, all table and/or additional tuple columns will be included.

start (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned records per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

order (string)
    The direction of the sort. Sorting is always done on the key of the table. Can be either ``asc`` or ``desc``. Default: ``desc``

The ``start`` and ``limit`` allow for pagination. Keep in mind that in-between two requests extra data may have been added to the table, causing a shift of the results. This generic stateless operation does not provide a reliable mechanism against that, so address it by overlapping your ``start`` parameter with rows of the previous query. In this example we overlap by 4:

    ?start=0&limit=50&order=desc
    ?start=45&limit=50&order=desc


.. rubric:: Response
.. code-block:: json

    {
      "record" : [ {
        "column" : [ {
          "name" : "gentime",
          "value" : {
            "type" : "TIMESTAMP",
            "timestampValue" : 1446650363464
          }
        }, {
          "name" : "pname",
          "value" : {
            "type" : "STRING",
            "stringValue" : "/YSS/SIMULATOR/FlightData"
          }
        } ]
      }, {
        "column" : [ {
          "name" : "gentime",
          "value" : {
            "type" : "TIMESTAMP",
            "timestampValue" : 1446650363667
          }
        }, {
          "name" : "pname",
          "value" : {
            "type" : "STRING",
            "stringValue" : "/YSS/SIMULATOR/FlightData"
          }
        } ]
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message TableData {
      message TableRecord {
        repeated ColumnData column = 1;
      }
      repeated TableRecord record = 1;
    }
