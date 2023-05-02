Object Archive (buckets)
========================

The Yamcs object archive is used to store general data objects (images, files, etc) which are generally unstructured information. 
The objects are grouped into ``buckets``; each bucket has a name and is simply a collection of related objects.

Inside a bucket each object is identified by an name and has associated a set of metadata. The name is usually (but not necessarily) a UNIX directory like path :file:`/a/b/c/` and the metadata is a list of ``key: value`` where both the key and the value are strings.

Yamcs supports two ways of storing the objects: inside the RocksDB database or on the server filesystem as files. For RocksDB buckets, each object is stored in a (key, value) record, the key is the object name prepended by a prefix identifying the bucket and the value is the object data.
For filesystem buckets, each bucket represents a directory on disk and the objects are the files in that directory (including subdirectories). The filesystem buckets do not support metadata currently.

A bucket is limited to 100MB in size and maximum 1000 objects. In addition, the HTTP API imposes a limit of 5MB for each uploaded object. Note that since the filesystem buckets can be changed outside Yamcs (just copying files in a directory) the total size limit or the number of objects limit may be exceeded.

The RocksDB buckets can be created in the configuration or programmatically using the :apidoc:`HTTP API <buckets>`.

The filesystem buckets can only be defined in the configuration by using the ``buckets`` configuration option as shown below.

The ``buckets`` keyword in :file:`etc/yamcs.yaml` will define a list of buckets.

.. code-block:: yaml

  buckets:
    - name: cfdpUp
      path: ../../cfdpUp

   
Options
-------

name (string)
    The name of the bucket. The name must contain only letters, digits or underscores.

path (string)
    If this option is present the bucket is a filesystem bucket and a directory with the given path will be created if not already existing. If omitted, this bucket will be stored binary in the Yamcs database (RocksDB).

maxSize (number)
    The maximum allowed size of the bucket in bytes.

maxObjects (number)
    The maximum allowed number of objects in this bucket.


.. note::

    The ``maxSize`` and ``maxObjects`` are enforced when *new* objects are added to the bucket. It is possible for limits to be lower than the actual usage. For example, when they have been reconfigured. Or, in the case of filesystem buckets, because content has changed outside of Yamcs.
