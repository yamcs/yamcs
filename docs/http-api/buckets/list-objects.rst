List Objects
============

List all objects from a bucket::

    GET /api/buckets/:instance/:bucketName

``_global`` can be used as instance name to list the objects from a bucket at the global level.


.. rubric:: Parameters

prefix (string)
    List only objects whose name start with prefix

delimiter (string)
    Return only objects whose name do not contain the delimiter after the prefix. For the other objects, the response contains (in the prefix response parameter) the name truncated after the delimiter. Duplicates are omitted.

The parameters ``prefix`` and ``delimiter`` provide filtering capabilities. These work similar to Google Cloud Storage and Amazon S3.

The ``delimiter`` allows the list to work in a directory mode despite the object namespace being flat. For example if the delimiter is set to "/", then listing the bucket containing objects "a/b", "a/c", "d", "e" and "e/f" returns objects "d" and "e" and prefixes "a/" and "e/".


.. rubric:: Response
.. code-block:: proto

    {
      "prefix": [ "a/"],
      "object": [{
        "name": "request-example-to-REST-Archive-CSV-API.txt",
        "created": "2018-05-28T08:25:19.809Z",
        "size": "869"
      }]
    }


.. rubric:: Response Schema (protobuf)
.. code-block:: proto

    message ObjectInfo {
      optional string name = 1;
      optional string created  = 2; //time in UTC format
      optional uint64 size  = 3; //size in bytes
      map<string, string> metadata = 4;
    }

    message ListObjectsResponse {
       repeated string prefix = 1;
       repeated ObjectInfo object = 2;
    }
