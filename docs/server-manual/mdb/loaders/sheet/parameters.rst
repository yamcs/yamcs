Parameters Sheet
================

This sheet contains parameter information.

This sheet may be named:

* **Parameters** for telemetered parameters.
* **DerivedParameters** for parameters that are the results of algorithm computations.
* **LocalParameters** for parameters that are local to Yamcs and that can be set by users.

A parameter when extracted from a binary packet has two forms: a raw value and an engineering value. The extraction from the raw packet is performed according to the encoding, whereas the conversion from raw to engineering value is performed by a calibrator. This sheet can also be used to specify parameters without encoding - if they are received already extracted, Yamcs can do only their calibration. Or it can be that a parameter is already calibrated, it can still be specified here to be able to associate alarms.

Empty lines can appear everywhere and are ignored. Comment lines starting with ``#`` on the first column can appear everywhere and are ignored.

* | **name**

  | The name of the parameter in the namespace.

* | **encoding**

  | Description on how to extract the raw type from the binary packet. See below for all supported encodings.

* | **raw type**

  | Documented below.

* | **eng type**

  | Documented below.

* | **eng unit**

  | Free-form textual description of unit(s). E.g. degC, W, V, A, s, us

* | **calibration**

  | Name of a calibration described in the Calibration sheet, leave empty if no calibration is applied

* | **description**

  | Optional human-readable text

* | **namespace:<NS-NAME>**

  | If present, these columns can be used to assign additional names to the parameters in the namespace NS-NAME. Any number of columns can be present to give additional names in different namespaces.


Encoding and Raw Types
^^^^^^^^^^^^^^^^^^^^^^

The raw type and encoding describe how the parameter is encoded in the binary packet. All types are case-insensitive.

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
      - fized size byte array
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


Engineering Types
^^^^^^^^^^^^^^^^^

Engineering types describe a parameter in its processed form (i.e. after any calibrations). All types are case-insensitive.

Depending on the combination of raw and engineering type, automatic conversion is applicable. For more advanced use cases, define and refer to a Calibrator in the Calibration Sheet

.. list-table::
    :widths: 20 40 40
    :header-rows: 1

    * - Type
      - Description
      - Automatic Conversion
    * - uint
      - Unsigned 32 bit integer - it corresponds to ``int`` in java and ``uint32`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - uint64
      - Unsigned 64 bit integer - it corresponds to ``long`` in java and ``uint64`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - int
      - Signed 32 bit integer - it corresponds to ``int`` in java and ``int32`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - int64
      - Signed 64 bit integer - it corresponds to ``long`` in java and ``int64`` in protobuf
      - From ``int``, ``uint`` or ``string``
    * - string
      - Character string - it corresponds to ``String`` in java and ``string`` in protobuf
      - From ``string``
    * - float
      - 32 bit floating point number - it corresponds to ``float`` in java and protobuf
      - From ``float``, ``int``, ``uint`` or ``string``
    * - double
      - 64 bit floating point number - it corresponds to ``double`` in java and protobuf
      - From ``float``, ``int``, ``uint`` or ``string``
    * - enumerated
      - A kind of string that can only be one out of a fixed set of predefined state values. It corresponds to ``String`` in java and ``string`` in protobuf.
      - From ``int`` or ``uint``. A Calibrator is required.
    * - boolean
      - A binary true/false value - it corresponds to 'boolean' in java and 'bool' in protobuf
      - From any raw type. Values equal to zero, all-zero bytes or an empty string are considered *false*.
    * - binary
      - Byte array - it corresponds to ``byte[]`` in java and ``bytes`` in protobuf.
      - From ``bytestream`` only
