Create Bucket
=============

Create a bucket::

    POST /api/buckets/:instance

``_global`` can be used as instance name to create a bucket at the global level.


.. rubric:: Example
.. code-block:: json

    {
      "name": "my_bucket"
    }


.. rubric:: Request Schema (Protobuf)
.. code-block:: proto

    message CreateBucketRequest {
      optional string name = 1;
    }
