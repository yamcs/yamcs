List Container Info
===================

List all containers defined in the Mission Database for the given Yamcs instance::

    GET /api/mdb/:instance/containers


.. rubric:: Parameters

namespace (string)
    Include containers under the specified namespace only

recurse (bool)
    If a ``namespace`` is given, specifies whether to list containers of any nested sub systems. Default ``no``.

q (string)
    The search keywords. This support searching on the namespace or name.


.. rubric:: Response
.. code-block:: json

    {
      "container" : [ {
        "name": "DHS",
        "qualifiedName" : "/YSS/SIMULATOR/DHS",
        "alias" : [ {
          "name" : "SIMULATOR_DHS",
          "namespace" : "MDB:OPS Name"
        }, {
          "name" : "DHS",
          "namespace" : "/YSS/SIMULATOR"
        } ],
        "maxInterval" : 1500,
        "baseContainer" : {
          "name": "ccsds-default",
          "qualifiedName" : "/YSS/ccsds-default"
        },
        "restrictionCriteria" : [ {
          "parameter" : {
            "name": "ccsds-apid",
            "qualifiedName" : "/YSS/ccsds-apid"
          },
          "operator" : "EQUAL_TO",
          "value" : "1"
        }, {
          "parameter" : {
            "name": "packet-id",
            "qualifiedName" : "/YSS/packet-id"
          },
          "operator" : "EQUAL_TO",
          "value" : "2"
        } ],
        "entry" : [ {
          "locationInBits" : 128,
          "referenceLocation" : "CONTAINER_START",
          "parameter" : {
            "name": "PrimBusVoltage1",
            "qualifiedName" : "/YSS/SIMULATOR/PrimBusVoltage1"
          }
        }, {
          "locationInBits" : 136,
          "referenceLocation" : "CONTAINER_START",
          "parameter" : {
            "name": "PrimBusCurrent1",
            "qualifiedName" : "/YSS/SIMULATOR/PrimBusCurrent1"
          }
        } ]
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListContainerInfoResponse {
      repeated mdb.ContainerInfo container = 1;
    }
