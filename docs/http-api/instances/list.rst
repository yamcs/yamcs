List Instances
==============

List all configured Yamcs instances::

    GET /api/instances


.. rubric:: Response
.. code-block:: json

    {
      "instances" : [ {
        "name" : "simulator",
        "missionDatabase" : {
          "configName" : "landing",
          "name" : "",
          "spaceSystem" : [ {
            "name" : "yamcs",
            "qualifiedName" : "/yamcs"
          }, {
            "name" : "YSS",
            "qualifiedName" : "/YSS",
            "sub" : [ {
              "name" : "SIMULATOR",
              "qualifiedName" : "/YSS/SIMULATOR"
            } ]
          }, {
            "name" : "GS",
            "qualifiedName" : "/GS"
          } ]
        },
        "processor" : [ {
          "name" : "realtime"
        } ]
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListInstancesResponse {
      repeated YamcsInstance instances = 1;
    }
