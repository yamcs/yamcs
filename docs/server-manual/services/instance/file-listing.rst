.. _file-listing:

File listing service
====================

This service provides an interface for retrieving and saving the list of files of a certain remote directory.

This may be coupled with the file transfer services, such as the :ref:`CFDP service <cfdp-service>`
-- which implements it --, to provide remote directory listing capabilities.

Implementing classes may make use of a :javadoc:`org.yamcs.filetransfer.FileListingParser` in order to parse a provided
file listing according to a certain specification (currently :javadoc:`org.yamcs.filetransfer.BasicListingParser` and
:javadoc:`org.yamcs.filetransfer.CsvListingParser` exist).

Class Name
----------

:javadoc:`org.yamcs.filetransfer.FileListingService`

Configuration
-------------

This service is defined in :file:`etc/yamcs.{instance}.yaml` but its configuration is implementation specific.
Here is an example of it being parametrised inside a file transfer service with a set file listing parser:

.. code-block:: yaml

    services:
      - class: org.yamcs.filetransfer.MyFileTransferService
        name: my-file-transfer
        args:
            fileListingServiceClassName: org.yamcs.filetransfer.MyFileListingService
            fileListingServiceArgs:
                automaticDirectoryListingReloads: false
                fileListingParserClassName: org.yamcs.filetransfer.BasicListingParser
                fileListingParserArgs:
                    directoryTerminators: ["/"]


Configuration Options
---------------------

**The interface has no common parameters** but these may be of use by certain implementations:

fileListingParserClassName
    Class to use to parse the file listing data.

fileListingParserArgs
    Arguments to pass to the FileListingParser used.

The implementation specific parameters (and defaults) can be found in their respective class:

* :ref:`CFDP service <cfdp-service>`

Parser Configuration Options
----------------------------

Each implementation of the file listing parsers have their own parameters.

BasicListingParser
~~~~~~~~~~~~~~~~~~

The BasicListingParser parses the file listing from a linebreak separated list of filenames.
Directories are detected by checking whether the file name ends with a directory terminator.

removePrependingRemotePath (boolean)
    Whether the filenames in the file listing contain the remote path as a prefix.
    Default: ``true``

directoryTerminators (list)
    Directory terminators, used to determine whether a file name corresponds to a directory. Parsing will remove all
    prepending and ending directory terminators.
    Default: ``["/"]``

CsvListingParser
~~~~~~~~~~~~~~~~

The CsvListingParser parses the file listing from a Comma Separated Value text, with each line representing a file and
each column one of its properties. Timestamps can be parsed as numbers or as strings in the ISO format.

useCsvHeader (boolean)
    Whether the parser should read the header of the CSV to determine what value goes to which property.
    Default: ``false``

protobufColumnNumberMapping (map)
    Mapping of the *RemoteFile* protobuf field names to the column number of the CSV (not used if *useCsvHeader* is ``true``).
    Default: *Column numbers are the same as the protobuf's (same order of fields)*

headerProtobufMapping (map)
    Mapping of the CSV column names in the header (when *useCsvHeader* is ``true``) to the protobuf fields names of *RemoteFile*.
    Default: *Same names as the protobuf fields*

timestampMultiplier (float)
    If timestamps are parsed as numbers, the multiplier to use to get the result in milliseconds.
    Default: ``1000``
