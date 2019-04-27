List Links
==========

List all links::

    GET /api/links

List all links for the given Yamcs instance::

    GET /api/links/:instance


.. rubric:: Response
.. code-block:: json

    {
      "link" : [ {
        "instance" : "simulator",
        "name" : "tm1",
        "type" : "HkDataHandler",
        "spec" : "",
        "stream" : "tm_realtime",
        "disabled" : false,
        "status" : "OK",
        "dataCount" : 34598,
        "detailedStatus" : "reading files from /storage/yamcs-incoming/simulator/tm"
      } ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListLinkInfoResponse {
      repeated yamcsManagement.LinkInfo link = 1;
    }
