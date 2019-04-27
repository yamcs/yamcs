Get Object
==========

Get an object::

    GET /api/buckets/:instance/:bucketName/:objectName

``_global`` can be used as instance name to get an object from a bucket at the global level.


.. rubric:: Response

The body of the response represents the object data. The ``Content-Type`` header is set to the content type of the object specified when uploading the object. If no ``Content-Type`` was specified when creating the object, the ``Content-Type`` of the response is set to ``application/octet-stream``.
