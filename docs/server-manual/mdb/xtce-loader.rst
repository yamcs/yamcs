XTCE Loader
===========

This loader reads an MDB saved in XML format compliant with the XTCE specification. For more information about XTCE, see `http://www.xtce.org <http://www.xtce.org>`_.

The loader is configured in ``etc/mdb.yaml`` or in the instance configuration by specifying the 'type' as ``xtce``, and providing the location of the XML file in the ``spec`` attribute.

General
-------

Yamcs uses XTCE data structures internally as much as possible following XTCE v1.2. However not all parts of the standard are supported. This chapter presents an overview of the not supported features and details when the implementation might differ from the standard. All the features that are not mentioned in this chapter should be supported.

Note that when reading the XML XTCE file Yamcs is on purpose tolerant, it ignores the tags it does not know and it also strives to be backward compatible with XTCE 1.0 and 1.1. Thus the fact that an XML file loads in Yamcs does not mean that is 100% valid. Please use a generic XML validation tool or the `xtcetools <https://gitlab.com/dovereem/xtcetools>`_ project to validate your XML file.


The following concepts are *not supported*:

* ``Stream`` - data is assumed to be injected into Yamcs as packets (see `Data Links </docs/server/Data_Link_Initialiser/>`), any stream processing has to be done as part of the data link definition and is not based on XTCE. 
* ``Message``
* ``ParameterSegmentRefEntry``
* ``ContainerSegmentRefEntry``
* ``BooleanExpression``
* ``DiscreteLookupList``
* ``ErrorDetectCorrectType``. Note that error detection/correction is implemented directly into the Yamcs data links.
* ``ContextSignificanceList``
* ``ParameterToSetList``
* ``ParameterToSuspendAlarmsOnSet``
* ``RestrictionCriteria/NextContainer``
* ``CommandVerifierType/(Comparison, BooleanExpression,ComparisonList)`` - soon to be implemented
* ``CommandVerifierType/ParameterValueChange`` - soon to be implemented

The other elements are supported one way or another, exceptions or changes from the specs are given in the sections below.


Header
^^^^^^

Only the version and date are supported. ``AuthorSet`` and ``NoteSet`` are ignored.


Data Encodings
^^^^^^^^^^^^^^

changeThreshold
^^^^^^^^^^^^^^^

changeThreshold is not supported.


FromBinaryTransformAlgorithm
""""""""""""""""""""""""""""

In XTCE the ``FromBinaryTransformAlgorithm`` can be specified for the ``BinaryDataEncoding``. It is not clear how exactly that is supposed to work. In Yamcs the ``FromBinaryTransformAlgorithm`` can be specified on any ``XyzDataEncoding`` and is used to convert from binary to the raw value which is supposed to be of type Xyz.


ToBinaryTransformAlgorithm
""""""""""""""""""""""""""

not supported for any data encoding


FloatDataEncoding
"""""""""""""""""

Yamcs supports IEEE754_1985, MILSTD_1750A and STRING encoding. STRING is not part of XTCE - if used, a StringDataEncoding can be attached to the FloatDataEncoding and the string will be extracted according to the StringDataEncoding and then parsed into a float or double according to the sizeInBits of FloatDataEncoding. DEC, IBM and TI encoding are not supported.


StringDataEncoding
""""""""""""""""""

For variable size strings whose size is encoded in front of the string, Yamcs allows to specify only for command arguments sizeInBitsOfSizeTag = 0. This means that the value of the argument will be inserted without providing the information about its size. The receiver has to know how to derive the size. This has been implemented for compatibility with other systems (e.g. SCOS-2k) which allows this - however it is not allowed by XTCE which enforces sizeInBitsOfSizeTag > 0. 


Data Types
^^^^^^^^^^


ValidRange
""""""""""

Not supported for any parameter type.


BooleanDataType
"""""""""""""""

In XTCE, each ``BooleanDataType`` has a string representation. In Yamcs the value is mapped to a org.yacms.parameter.BooleanValue or the protobuf equivalnet that is a wrapper for a boolean (either true or false in all sane programming languages). The string value is neverhteless supported in comparisons and mathalgorithms but they are converted internally to the boolean value. If you want to get to the string representation from the client, use an ``EnumeratedParameterType``.


RelativeTimeDataType
""""""""""""""""""""
not supported.


Monitoring
----------

ParameterSetType
^^^^^^^^^^^^^^^^

``parameterRef`` is not supported. According to XTCE doc this is "Used to include a Parameter defined in another sub-system in this sub-system". It is not clear what it means "to include". Parameters from other space systems can be referenced using a fully qualified name or a relative name.

ParameterProperties
^^^^^^^^^^^^^^^^^^^

* ``PhysicalAddressSet`` is not supported.
* ``SystemName`` is not supported.
* ``TimeAssociation`` is not supported.


Containers
^^^^^^^^^^

``BinaryEncoding`` not supported in the container definitions.


StringParameterType
^^^^^^^^^^^^^^^^^^^

Alarms are not supported.


Commanding
----------

Aggregates and Arrays are not supported for commands (they are for telemetry).


ArgumentRefEntry
^^^^^^^^^^^^^^^^

* ``IncludeCondition`` is not supported
* ``RepeatEntry`` is not supported
