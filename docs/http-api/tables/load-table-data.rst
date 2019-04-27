Load Table Data
===============

Load data into a table::

     POST /api/archive/:instance/tables/:name/data

Use HTTP header (antyhing else is not supported)::

    Content-Type: application/protobuf

The data is a stream of ``Row``s, each ``Row`` being composed of a list of ``Cell``s. Each ``Row`` is preceded by its size in bytes varint encoded.

Each row has an optional associated list of ``ColumnInfo`` messages that define the table columns conainted in the row. The <ColumnInfo> message assigns an integer ``id`` for each column and the ``id`` is present in each cell belonging to that column (this is done in order to avoid sending the ``ColumnInfo`` with each ``Cell``). The column id starts from 0 and is incremented with each new column present in the load. The ids are only valid during one single load.

The table has to exist in order to load data into it.

Chuncked data encoding can be used to send a large number of rows without knowing the total size in advance.

.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message ColumnInfo {
      optional uint32 id = 1;
      optional string name = 2;
      //one of the types defined in org.yamcs.yarch.DataType
      //INT, STRING, DOUBLE, PROTOBUF(x.y.z), etc
      optional string type = 3;
    }

    message Cell {
       optional uint32 columnId = 1;
       optional bytes data = 2;
    }

    message Row {
      //the column info is only present for new columns in a stream of Row messages
      repeated ColumnInfo column = 1;
      repeated Cell cell = 2;
    }


.. rubric:: Response

Response can be Protobuf or JSON depending on the ``Accept`` header of the request.

If there is an error during the load, the HTTP status will be 4xy or 5xy and the ``msg`` will contain more information about the error. One possible error could be that the table has defined a (primary) key and one of the loaded rows contains no value for one of the columns of the key.

As the data is streamed to the server, if there is an error, there would have been certanly more data sent after the bad row, so the error is not about the last row sent. The numRowsLoaded response parameter can be used to know how many rows have been successfully loaded (the bad row is the numRowsLoaded+1 in the stream).

Note that if the server detects an error, it will send back the error message and close the connection regardless of the Keep-Alive option in the request header.


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message TableLoadResponse {
       optional uint32 numRowsLoaded = 1;
    }

    message RestExceptionMessage {
       optional string type = 1;
       optional string msg = 2;
       optional uint32 numRowsLoaded = 100;
    }
