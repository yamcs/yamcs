List Container Info
===================

List all containers defined in the Mission Database for the given Yamcs instance::

    GET /api/mdb/:instance/containers


.. rubric:: Parameters

q (string)
    The search keywords. This supports searching on the namespace or name.

system (string)
    List only direct child sub-systems or containers of the specified system. For example when querying the system "/a" against an MDB with containers "/a/b/c" and "/a/c", the result returns the sub system "/a/b" and the container "/a/c".

pos (integer)
    The zero-based row number at which to start outputting results. Default: ``0``

limit (integer)
    The maximum number of returned containers per page. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``100``

When ``system`` and ``q`` are used together, matching containers at any depth are returned, starting from the specified space system.


.. rubric:: Response
.. code-block:: json

    {
      "containers" : [ {
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

    message ListContainersResponse {
      repeated mdb.ContainerInfo containers = 1;
    }
