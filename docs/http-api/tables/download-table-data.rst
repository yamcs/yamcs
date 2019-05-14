Download Table Data
===================

Download archived table data::

    GET /api/archive/:instance/downloads/tables/:table


.. rubric:: Parameters

cols (array of strings)
    The columns to be included in the result. Both these notations are accepted:

    * ``?cols=rectime,gentime,pname``
    * ``?cols[]=rectime&cols[]=gentime&cols[]=pname``

    If unspecified, all table and/or additional tuple columns will be included.

order (string)
    The order of the returned results. Can be either ``asc`` or ``desc``. Default: ``asc``

format (string)
    If it is ``dump``, the response will contain low level information that allows the data to be used to load the table (see below).


.. rubric:: Response

The response is a stream of individual table records or rows. When using Protobuf, every table record is delimited by its byte size.


.. rubric:: Response Schema (protobuf)

If non-dump format is requested, response is of type:

.. code-block:: proto

    message TableData {
      message TableRecord {
        repeated ColumnData column = 1;
      }
      repeated TableRecord record = 1;
    }


If ``format=dump`` parameter is used, the response is a series of ``Row``\s, each ``Row`` being composed of a list of ``Cell``\s.

Each row has an optional associated list of ``ColumnInfo`` messages that define the table columns conainted in the row. The <ColumnInfo> message assigns an integer ``id`` for each column and the ``id`` is present in each cell belonging to that column (this is done in order to avoid sending the ``ColumnInfo`` with each ``Cell``). The column id starts from 0 and are incremented with each new column found. The ids are only valid during one single dump.

The dumped data does not contain information on any table characteristics such as (primary) key, partitioning or other storage options.


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
