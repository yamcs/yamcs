DataTypes Sheet
===============

This sheet describes data types that can then be used in the definition of :doc:`parameters <parameters>` and :doc:`command arguments <commands>`.

``type name`` (required)
    Name of the type used as a reference in the parameter and command sheets.

``eng type`` (required)
    Engineering type. One of:

    * ``uint``: unsigned 32 bit integer
    * ``uint64``: unsigned 64 bit integer
    * ``int``: signed 32 bit integer
    * ``int64``: signed 64 bit integer
    * ``enumerated``: enumeration (integer to string)
    * ``float``: 32 bit floating point number
    * ``double``: 64 bit floating point number
    * ``boolean``: true or false
    * ``string``: text value
    * ``binary``: byte array
    * ``time``: absolute time

    It is also possible to define an aggregate or array type.

    See: :ref:`engineering-types`.

``raw type``
    See: :ref:`raw-encoding`.

    A parameter when extracted from a binary packet has two forms: a raw value and an engineering value. The extraction from the raw packet is performed according to the encoding, whereas the conversion from raw to engineering value is performed by a calibrator.
    
    Raw types are optional for use with parameters that do not require encoding. For example because they are already extracted. Then Yamcs can only do their calibration. Or it can be that a parameter is already calibrated, then it can still be specified here to be able to associate alarms.

``encoding``
    See: :ref:`raw-encoding`.

``eng unit``
    Unit of measure. For informational purpose only.

``calibration``
    Reference to a calibrator defined in the :doc:`Calibration sheet <calibration>`. Leave empty if no calibration is applied.

``initial value``
    Initial (default) value given to a parameter or command argument.

    Note that this value can be overwritten for specific parameters, or command arguments using a column of the same name in the :doc:`Commands <commands>` and :doc:`Parameters <parameters>` sheets.

    .. include:: _includes/initial-value.rst

``description``
    A description for the parameter or command argument. Should be one line.

``long description``
    Long textual description. In Markdown format.


.. _raw-encoding:

Encoding and Raw Types
^^^^^^^^^^^^^^^^^^^^^^

The columns ``raw type`` and ``encoding`` describe how the parameter is encoded in the binary packet. All types are case-insensitive.


Unsigned Integers
"""""""""""""""""

Raw type: ``uint``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``unsigned(<n>,<BE|LE>)``
      - unsigned integer
    * - ``<n>``
      - shortcut for ``unsigned(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Signed Integers
"""""""""""""""

Raw type: ``int``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``twosComplement(<n>,<BE|LE>)``
      - two's complement encoding
    * - ``signMagnitude(<n>,<BE|LE>)``
      - sign magnitude encoding - first (or last for LE) bit is the sign, the remaining bits represent the magnitude (absolute value).
    * - ``<n>``
      - shortcut for ``twosComplement(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Floats
""""""

Raw type: ``float``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``ieee754_1985(<n>,<BE|LE>)``
      - IEE754_1985 encoding
    * - ``<n>``
      - shortcut for ``ieee754_1985(<n>,BE)``

Where:

* ``n`` is the size in bits
* ``LE`` = little endian
* ``BE`` = big endian


Booleans
""""""""

Raw type: ``boolean``

Encoding: Leave empty. 1 bit is assumed.


String
""""""

Raw type: ``string``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``fixed(<n>, <charset>)``
      - fixed size string. The string has to start at a byte boundary inside the container.
    * - ``PrependedSize(<x>, <charset>)``
      - string whose length in bytes is specified by the first ``x`` bits of the array
    * - ``<n>``
      - shortcut for ``fixed(<n>)``
    * - ``terminated(<0xBB>, <charset><m>)``
      - terminated string

Where:

``n`` is the size in bits. Only multiples of 8 are supported.

``x`` is the size in bits of the size tag. Only multiples of 8 are supported. The size must be expressed in bytes.

``charset`` is one of the `charsets supported by java <https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html>`_ (UTF-8, ISO-8859-1, etc). Default: UTF-8.

``0xBB`` specifies a byte that is the string terminator. Pay attention to the parameters following this one; if the terminator is not found the entire buffer will be consumed.


Binary
""""""

Raw type: ``binary``

Encoding:

.. list-table::
    :widths: 40 60
    :header-rows: 1

    * - Encoding
      - Description
    * - ``fixed(<n>)``
      - fixed size byte array
    * - ``PrependedSize(<x>)``
      - byte array whose size in bytes is specified in the first ``x`` bits of the array
    * - ``<n>``
      - shortcut for ``fixed(<n>)``

Where:

``n`` is the size in bits. Only multiples of 8 are supported and it has to start at a byte boundary.

``x`` is the size in bits of the size tag. Note that while ``x`` can be any number <= 32, the byte array has to start at a byte boundary.


Custom
""""""

Raw type: *any*

Encoding: ``custom(<n>,algorithm)``

The decoding will be performed by a user defined algorithm.

* ``<n>`` is optional and may be used to specify the size in bits of the entry in the container (in case the size is fixed) - it is used for optimizing the access to the parameters following this one.
* ``algorithm`` the name of the algorithm - it has to be defined in the *Algorithms* sheet


.. _engineering-types:

Engineering Types
^^^^^^^^^^^^^^^^^

Engineering types describe a parameter in its processed form (i.e. after any calibrations). All types are case-insensitive.

Depending on the combination of raw and engineering type, automatic conversion is applicable. For more advanced use cases, define and refer to a calibrator in the :doc:`Calibration sheet <calibration>`.

.. list-table::
    :widths: 20 40 40
    :header-rows: 1

    * - Type
      - Description
      - Automatic Conversion
    * - ``uint``
      - Unsigned 32 bit integer - it corresponds to ``int`` in Java.
      - From ``int``, ``uint`` or ``string``
    * - ``uint64``
      - Unsigned 64 bit integer - it corresponds to ``long`` in Java.
      - From ``int``, ``uint`` or ``string``
    * - ``int``
      - Signed 32 bit integer - it corresponds to ``int`` in Java.
      - From ``int``, ``uint`` or ``string``
    * - ``int64``
      - Signed 64 bit integer - it corresponds to ``long`` in Java.
      - From ``int``, ``uint`` or ``string``
    * - ``string``
      - Character string - it corresponds to ``String`` in Java.
      - From ``string``
    * - ``float``
      - 32 bit floating point number - it corresponds to ``float`` in Java.
      - From ``float``, ``int``, ``uint`` or ``string``
    * - ``double``
      - 64 bit floating point number - it corresponds to ``double`` in Java.
      - From ``float``, ``int``, ``uint`` or ``string``
    * - ``enumerated``
      - A kind of string that can only be one out of a fixed set of predefined state values. It corresponds to ``String`` in Java.
      - From ``int`` or ``uint``. A Calibrator is required.
    * - ``boolean``
      - A binary true/false value - it corresponds to 'boolean' in Java.
      - From any raw type. Values equal to zero, all-zero bytes or an empty string are considered *false*.
    * - ``binary``
      - Byte array - it corresponds to ``byte[]`` in Java.
      - From ``binary`` only.
