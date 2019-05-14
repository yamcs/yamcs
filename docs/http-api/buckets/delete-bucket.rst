Delete Bucket
=============

Deleting a bucket means also deleting all objects that are part of it.

Delete a bucket::

    DELETE /api/bucket/:instance/:bucketName

``_global`` can be used as instance name to delete a bucket at the global level.
