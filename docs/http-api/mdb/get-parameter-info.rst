Get Parameter Info
==================

Return the data for the given parameter::

    GET /api/mdb/:instance/parameters/:namespace/:name


.. rubric:: Response
.. code-block:: json

    {
      "name": "BatteryVoltage2",
      "qualifiedName" : "/YSS/SIMULATOR/BatteryVoltage2",
      "alias" : [ {
        "name" : "SIMULATOR_BatteryVoltage2",
        "namespace" : "MDB:OPS Name"
      }, {
        "name" : "BatteryVoltage2",
        "namespace" : "/YSS/SIMULATOR"
      } ],
      "type" : {
        "engType" : "integer",
        "dataEncoding" : "IntegerDataEncoding(sizeInBits:8, encoding:unsigned, defaultCalibrator:null byteOrder:BIG_ENDIAN)"
      },
      "dataSource" : "TELEMETERED"
    }


.. rubric:: Bulk

Combine multiple parameter queries in one and the same request using this address::

    GET /api/mdb/:instance/parameters/bulk

Specify the parameter IDs in the request body:

.. code-block:: json

    {
      "id" : [ {
        "name": "YSS_ccsds-apid",
        "namespace": "MDB:OPS Name"
      }, {
        "name": "YSS_packet-type",
        "namespace": "MDB:OPS Name"
      } ]
    }


POST requests are also allowed, because some HTTP clients do not support GET with a request body.

In the response the requested parameter ID is returned for every match. Example:

.. code-block:: json

    {
      "response" : [ {
        "id" : {
          "name" : "YSS_ccsds-apid",
          "namespace" : "MDB:OPS Name"
        },
        "parameter" : {
          "name": "ccsds-apid",
          "qualifiedName" : "/YSS/ccsds-apid",
          "aliases" : [ {
            "name" : "YSS_ccsds-apid",
            "namespace" : "MDB:OPS Name"
          }, {
            "name" : "ccsds-apid",
            "namespace" : "/YSS"
          } ],
          "type" : {
            "engType" : "integer",
            "dataEncoding" : "IntegerDataEncoding(sizeInBits:11, encoding:unsigned, defaultCalibrator:null byteOrder:BIG_ENDIAN)"
          },
          "dataSource" : "TELEMETERED"
        }
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ParameterInfo {
      optional string name = 1;
      optional string qualifiedName = 2;
      optional string shortDescription = 3;
      optional string longDescription = 4;
      repeated yamcs.NamedObjectId alias = 5;
      optional ParameterTypeInfo type = 6;
      optional DataSourceType dataSource = 7;
    }


.. rubric:: Bulk Request Schema (protobuf)
.. code-block:: proto

    message BulkGetParameterRequest {
      repeated yamcs.NamedObjectId id = 1;
    }


.. rubric:: Bulkd Response Schema (protobuf)
.. code-block:: proto

    message BulkGetParameterResponse {
      message GetParameterResponse {
        optional yamcs.NamedObjectId id = 1;
        optional mdb.ParameterInfo parameter = 2;
      }

      repeated GetParameterResponse response = 1;
    }
