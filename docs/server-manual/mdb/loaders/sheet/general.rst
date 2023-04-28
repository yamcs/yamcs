General Sheet
=============

This sheet is required and allows global properties to be defined. Apart from the column headers, the sheet should contain only a single row.

``format version`` (required)
    Used by the loader to ensure a compatible spreadsheet structure.

    The latest format version is 7.1.

    The earliest supported format is 5.3.

``name`` (required)
    Name of the space system. All definitions in this system will be added to this system.

``document version`` (required)
    Available to the spreadsheet author to track versions in an arbitrary manner.

    If the :doc:`ChangeLog <changelog>` sheet is used, the document version should match the version of the latest changelog entry.
