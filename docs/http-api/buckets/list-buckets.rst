List Buckets
============

List all buckets for the given Yamcs instance::

    GET /api/buckets/:instance

``_global`` can be used as instance name to list the buckets at the global level.

.. rubric:: Response
.. code-block:: proto

    {
      "buckets": [{
        "name": "my_bucket",
        "size": "1542391",
        "numObjects": 7
      }, {
        "name": "user.admin",
        "size": "1738",
        "numObjects": 2
      }]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message BucketInfo {
      optional string name = 1;
      optional uint64 size  = 2; //total size in bytes of all objects in the bucket (metadata is not counted)
      optional uint32 numObjects  = 3; //number of objects in the bucket
    }

    message ListBucketsResponse {
      repeated BucketInfo buckets = 1;
    }
