CFDP Upload
===========

Upload, using the CFDP protocol, an object from a bucket to a remote CFDP entity::

    POST /api/cfdp/{instance}/{bucketName}/{objectName}

``_global`` can be used as an instance name to refer to a bucket at the global level.

.. rubric:: Parameters

target (string)
    **Required** The destination path and filename (eg: a/local/path/some_filename) at the remote CFDP entity.

reliable (boolean)
    Set to ``True`` if reliable (class 2) CFDP transfer should be used, otherwise unreliable (class 1) CFDP transfer will be used. Defaults to ``False``.

overwrite (boolean)
    Set to ``True`` if an already existing destination should be overwritten. Defaults to ``True``

createpath (boolean)
    Set to ``True`` if the destination path should be created if it does not exist. Defaults to ``True``

.. rubric:: Response
.. code-block:: json

    {
      "transferId": 1
    } 
