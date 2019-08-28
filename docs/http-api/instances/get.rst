Get Instance Detail
===================

Get data on a Yamcs instance::

    GET /api/instances/:instance


.. rubric:: Response
.. code-block:: json

    {
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
    }


If an instance does not have web services enabled, it will be listed among the results, but none of its URLs will be filled in.


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message YamcsInstance {
      required string name = 1;
      optional MissionDatabase missionDatabase = 3;
      repeated ProcessorInfo processor = 4;
    }
