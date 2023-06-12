Functions
=========

.. index:: COALESCE
   single: Function; COALESCE

COALESCE()
----------

.. code-block::

   COALESCE(value1, value2, value3, ...)

The ``COALESCE()`` function returns the first value from the list of arguments that is not ``NULL``, or ``NULL`` if there is none.


.. index:: UNHEX
   single: Function; UNHEX

UNHEX()
-------

.. code-block::

   UNHEX(str)

The ``UNHEX()`` function interpretes the given argument as a hexadecimal string, and returns a binary value.


.. index:: EXTRACT_SHORT
   single: Function; EXTRACT_SHORT

EXTRACT_SHORT()
---------------

.. code-block::

   EXTRACT_SHORT(binary, offset)

Decodes 16 bits signed at the specified offset, returning a short value.


.. index:: EXTRACT_USHORT
   single: Function; EXTRACT_USHORT

EXTRACT_USHORT()
----------------

.. code-block::

   EXTRACT_USHORT(binary, offset)

Decodes 16 bits unsigned at the specified offset, returning an integer value.


.. index:: EXTRACT_INT
   single: Function; EXTRACT_INT

EXTRACT_INT()
-------------

.. code-block::

   EXTRACT_INT(binary, offset)

Decodes 32 bits signed at the specified offset, returning an integer value.


.. index:: EXTRACT_U3BYTES
   single: Function; EXTRACT_U3BYTES

EXTRACT_U3BYTES()
-----------------

.. code-block::

   EXTRACT_U3BYTES(binary, offset)

Decodes 24 bits unsigned at the specified offset, returning an integer value.


.. index:: COUNT
   single: Function; COUNT

COUNT()
-------

.. code-block::

   COUNT(*)
   COUNT(column)

Aggregate function that counts the number of rows in a table that match the specified WHERE clause.


.. index:: SUBSTRING
   single: Function; SUBSTRING

SUBSTRING()
-----------

.. code-block::

   SUBSTRING(str, offset)
   SUBSTRING(str, offset, length)

Returns a substring of the given string, starting at the specified character offset.


.. index:: SUM
   single: Function; SUM

SUM()
-----

.. code-block::

   SUM(column)

Aggregate function that returns the sum of the values of a given column for all rows in a table that match the specified WHERE clause.
