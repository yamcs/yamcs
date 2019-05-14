Buckets
=======

Buckets represent a simple mechanism for storing user objects (binary data chunks such as images, monitoring lists, displays...) together with some metadata. Buckets can be created globally or asociated with an instance.

The metadata is represented by simple (key,value) pairs where both key and value are strings.

By default each user has a bucket named ``user.username`` which can be used without extra privileges. Additional buckets may be created and used if the user has the required privileges. The user bucket will be created automatically when the user tries to access it.

Buckets can be created at global level or at instance level. 
The following limitations are implemented in order to prevent disk over consumption and keep the service responsive:

* The maximum size of an upload including data and metadata is 5MB.
* The maximum number of objects in one bucket is 1000.
* The maximum size of an bucket 100MB (counted as the sum of the size of the objects within the bucket).
* The maximum size of the metadata is 16KB (counted as the sum of the length of the keys and values).

.. toctree::
    :maxdepth: 1

    create-bucket
    list-buckets
    delete-bucket
    upload-object
    list-objects
    get-object
    delete-object
