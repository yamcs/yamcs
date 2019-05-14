Get Packet Index
================

Get the index of stored packets for the given instance::

    GET /api/archive/:instance/packet-index

.. rubric:: Parameters

start (string)
    Filter the lower bound of the index entries. Specify a date string in ISO 8601 format.

stop (string)
    Filter the upper bound of the index entries. Specify a date string in ISO 8601 format.

mergeTime (integer)
    Value in milliseconds that indicates the maximum gap before two consecutive index ranges are merged together. Default: ``2000``

limit (integer)
    | The maximum number of returned entries. Choose this value too high and you risk hitting the maximum response size limit enforced by the server. Default: ``1000``.
    | Note that in general it is advised to control the size of the response via ``mergeTime``, rather than via ``limit``.


.. rubric:: Example
.. code-block:: json

    {
      "group": [{
        "id": {
          "name": "/YSS/SIMULATOR/DHS"
        },
        "entry": [{
          "start": "2018-04-26T10:45:26.671Z",
          "stop": "2018-04-26T10:59:22.679Z",
          "count": 134
        }]
      }, {
        "id": {
          "name": "/YSS/SIMULATOR/FlightData"
        },
        "entry": [{
          "start": "2018-04-26T10:45:20.621Z",
          "stop": "2018-04-26T10:59:22.882Z",
          "count": 4155
        }]
      }, {
        "id": {
          "name": "/YSS/SIMULATOR/Power"
        },
        "entry": [{
          "start": "2018-04-26T10:45:26.671Z",
          "stop": "2018-04-26T10:59:22.679Z",
          "count": 134
        }]
      }, {
        "id": {
          "name": "/YSS/SIMULATOR/RCS"
        },
        "entry": [{
          "start": "2018-04-26T10:45:26.671Z",
          "stop": "2018-04-26T10:59:22.679Z",
          "count": 134
        }]
      }]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message IndexResponse {
      repeated IndexGroup group = 1;
    }
