List Services
=============

List global services::

    GET /api/services/_global

List all services for the given Yamcs instance::

    GET /api/services/:instance


.. rubric:: Response
.. code-block:: json

    {
      "services" : [ {
        "instance" : "simulator",
        "name" : "org.yamcs.tctm.DataLinkInitialiser",
        "state" : "RUNNING"
      }, {
        "instance" : "simulator",
        "name" : "org.yamcs.archive.XtceTmRecorder",
        "state" : "RUNNING"
      }, {
        "instance" : "simulator",
        "name" : "org.yamcs.archive.FSEventDecoder",
        "state" : "RUNNING"
      }, {
        "instance" : "simulator",
        "name" : "org.yamcs.archive.PpRecorder",
        "state" : "RUNNING"
      }
      ]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListServicesResponse {
      repeated yamcsManagement.ServiceInfo services = 1;
    }
