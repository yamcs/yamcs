CFDP Download
=============

Download, using the CDFP protocol, a file from a remote CFDP entity to an object in a bucket::

    GET /api/cdfp/{instance}/{bucketNamme}/{objectName}

``_global`` can be used as an instance name to refer to a bucket at the global level.

If the bucketName is ``user.username`` then a bucket will be created automatically if it does not exist. Otherwise the bucket must exist before being downloaded to.

.. rubric:: Parameters

target (string)
    **Required** The source path and filename (eg: a/local/path/some_filename) at the remote CFDP entity.

reliable (boolean)
    Set to ``True`` if acknowledged (CFDP Class 2) transfers are to be used. Defaults to ``False``

.. rubric:: Response
.. code-block:: json

    {
      "transferId": 1
    }
