Object Archive (buckets)
========================

The Yamcs object archive is used to store general data objects (images, files, etc) which are generally unstructured information. 
The objects are grouped into ``buckets``; each bucket has a name and is simply a collection of related objects.

Inside a bucket each object is identified by an name and has associated a set of metadata. The name is usually (but not necessarily) a UNIX directory like path :file:`/a/b/c/` and the metadata is a list of ``key: value`` where both the key and the value are strings.

Yamcs supports two ways of storing the objects: inside the RocksDB database or on the server filesystem as files. For RocksDB buckets, each object is stored in a (key, value) record, the key is the object name prepended by a prefix identifying the bucket and the value is the object data.
For filesystem buckets, each bucket represents a directory on disk and the objects are the files in that directory (including subdirectories). The filesystem buckets do not support metadata currently.

A bucket is limited to 100MB in size and maximum 1000 objects. In addition, the HTTP API imposes a limit of 5MB for each uploaded object. Note that since the filesystem buckets can be changed outside Yamcs (just copying files in a directory) the total size limit or the number of objects limit may be exceeded.

The RocksDB buckets can be created in the configuration or programmatically using the :apidoc:`HTTP API <buckets>`.


Buckets
-------

The ``buckets`` keyword in :file:`etc/yamcs.yaml` defines a list of buckets.

.. code-block:: yaml

  buckets:
    - name: mybucket
    - name: cfdpUp
      path: ../../cfdpUp

   
Options
~~~~~~~

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

accessRules (list of maps)
    Optional list of object specific access rules for the bucket. If not provided, permissions are handled at the Object Privilege level (``ReadBucket`` and ``ManageBucket``) which apply to the entire bucket.

    role (string)
        **Required.** User role to which the object access rules will apply to.

    read (list of patterns)
        List of paths (or regex patterns of paths) the role is allowed to read. The user should still have at least the bucket's ``ReadBucket`` privilege for these to apply.

    write (list of patterns)
        List of paths (or regex patterns of paths) the role is allowed to write (create, edit, rename, delete, etc.). The user should still have at least the bucket's ``ManageBucket`` privilege for these to apply.

.. code-block:: yaml

buckets:
  - name: displays
    accessRules:
      - role: Operator
        read:
          - .*\.opi
          - .*\.js


Bucket Providers
----------------

A plugin mechanism is available to add custom bucket providers. Currently the only such implementation is called ``remote-yamcs``, which allows Yamcs to interact with a bucket on another server of Yamcs.

This can be activated by setting the ``bucketProviders`` property in :file:`etc/yamcs.yaml`. In the following example, Yamcs will reach out to a yamcs2 server with the provided credentials (Basic Auth only) to locate a remote bucket named ``foo`` and map this to a local bucket named ``bar``. Any read or write in ``bar`` is actually done on the yamcs2 server in the ``foo`` bucket:

.. code-block:: yaml

  bucketProviders:
    - type: remote-yamcs
      yamcsUrl: https://yamcs2.example.com
      username: admin
      password: test
      buckets:
        - name: foo
          localName: bar

Options
~~~~~~~

yamcsUrl (string)
  **Required.** The URL of the remote Yamcs server; The URL has to include http or https.

username (string)
  Username to connect to the upstream Yamcs server (if authentication is enabled); has to be set together with password.

password (string)
  Password to connect to the upstream Yamcs server (if authentication is enabled); has to be set together with username.

verifyTls (boolean)
    If the connection is over TLS (when ``yamcsUrl`` starts with https), this option can enable/disable the verification of the server certificate against local accepted CA list. Default: true

buckets (list of maps)
  Buckets to consider. Any remote bucket not in this list is ignored. For each bucket at least the ``name`` should be specified. Specify also ``localName`` if you want the local name to be different than the remote name.

