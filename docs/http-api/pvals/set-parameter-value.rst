Set Parameter Value
===================

Parameters are usually read-only. But software parameters can be updated::

    PUT /api/processors/:instance/:processor/parameters/:namespace/:name


.. rubric:: Example
.. code-block:: json

    {
      "type" : "UINT32",
      "uint32Value" : 123
    }


.. rubric:: Multi-set

To update multiple parameters at once, send a request to this address::

    POST /api/processors/:instance/:processor/parameters/mset

.. code-block:: json

    {
      "request" : [ {
        "id": {
          "name": "/YSS/SIMULATOR/AllowCriticalTC1"
        },
        "value": {
          "type" : "BOOLEAN",
          "booleanValue" : true
        }
      } ]
    }


.. rubric:: Request Schema (protobuf)
.. code-block:: proto

    message Value {
      enum Type {
        FLOAT = 0;
        DOUBLE = 1;
        UINT32 = 2;
        SINT32 = 3;
        BINARY = 4;
        STRING = 5;
        TIMESTAMP = 6;
        UINT64 = 7;
        SINT64 = 8;
        BOOLEAN = 9;
      }
      required Type type = 1;
      optional float floatValue = 2;
      optional double doubleValue = 3;
      optional sint32 sint32Value = 4;
      optional uint32 uint32Value = 5;
      optional bytes binaryValue = 6;
      optional string stringValue = 7;
      optional int64 timestampValue = 8;
      optional uint64 uint64Value = 9;
      optional sint64 sint64Value = 10;
      optional bool booleanValue = 11;
    }


.. rubric:: Bulk Request Schema (protobuf)
.. code-block:: proto

    message BulkSetParameterValueRequest {
      message SetParameterValueRequest {
        optional yamcs.NamedObjectId id = 1;
        optional yamcs.Value value = 2;
      }
      repeated SetParameterValueRequest request = 1;
    }
