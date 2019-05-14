List Clients
============

List all clients::

    GET /api/clients

List all clients for the given Yamcs instance::

    GET /api/instances/:instance/clients

List all clients for the given processor::

    GET /api/processors/:instance/:processor/clients


.. rubric:: Response
.. code-block:: json

    {
      "client": [{
        "instance": "simulator",
        "id": 6,
        "username": "admin",
        "applicationName": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_4) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/11.1 Safari/605.1.15",
        "processorName": "realtime",
        "state": "CONNECTED",
        "loginTime": "1524258579036",
        "loginTimeUTC": "2018-04-20T21:09:02.036Z"
      }]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ListClientsResponse {
      repeated yamcsManagement.ClientInfo client = 1;
    }
