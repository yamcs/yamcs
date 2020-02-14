Read Table Rows
===============

Stream the row contents of a table::

    POST /api/archive/{instance}/tables/{table}:readRows


.. rubric:: Parameters

cols (array of strings)
    The columns to be included in the result. Both these notations are accepted:

    * ``?cols=rectime,gentime,pname``
    * ``?cols[]=rectime&cols[]=gentime&cols[]=pname``

    If unspecified, all table and/or additional tuple columns will be included.


.. rubric:: Response

The response is a stream of individual table rows. When using Protobuf, every row is delimited by its byte size.


.. rubric:: Response Schema (protobuf)

.. code-block:: proto

    message Row {
      message ColumnInfo {
        optional uint32 id = 1;
        optional string name = 2;  
        optional string type = 3;
        optional string protoClass = 4; //the name of the class implementing the proto object if the     dataType=PROTOBUF
      }
    
      message Cell {
        optional uint32 columnId = 1; 
        optional bytes data = 2;
      }
    
      //the column info is only present for new columns in a stream of Row messages
      repeated ColumnInfo columns = 1; 
      repeated Cell cells = 2;   
    }

The ``ColumnInfo`` message assigns an integer ``id`` for each column and the ``id`` is present in each cell belonging to that column (this is done in order to avoid sending the ``ColumnInfo`` with each ``Cell``). The column id starts from 0 and are incremented with each new column found. The ids are only valid during one single dump.

The dumped data does not contain information on any table characteristics such as (primary) key, partitioning or other storage options.
