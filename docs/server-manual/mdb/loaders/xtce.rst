XTCE Loader
===========

This loader reads TM/TC definitions from an XML file compliant with the XTCE exchange format coordinated by OMG. The Yamcs database is very close to XTCE, which makes this mapping relatively straightforward. For more information about XTCE, see http://www.xtce.org.



Configuration
-------------

The loader is configured in :file:`etc/mdb.yaml` or in the instance configuration by specifying the type as ``xtce``, and providing the location of the XML file in the ``file`` attribute.

.. code-block:: yaml

    - type: "xtce"
      args:
        file: "BogusSAT.xml"
        autoTmPartitions: true
        #fileset: ["a*.xml", "b.xml"]
        
**Configuration Options**

file (string)
   The filename to be loaded. Either this or the ``fileset`` attribute are required

fileset (string or list of strings)
   Can be used to load multiple XML files. A glob pattern can be used to match multiple files and/or the files can be specified in the list. The ``**`` for matching directories recursively is not supported.

   If the ``fileset`` option is used, the ``subLoader`` cannot be used to load child subsystems. This is because it is not possible to specify which subsystem will be the parent of the child.

autoTmPartitions (boolean)
   If true, Yamcs will automatically mark to be used as archive partitions all containers which do not have a parent.

   If this option is false, the containers can still be manually marked by using the  ancillary data property ``UseAsArchivingPartition``:

   .. code-block:: xml

        <SequenceContainer>
          ....
          <AncillaryDataSet>
              <AncillaryData name="Yamcs">UseAsArchivingPartition</AncillaryData>
           </AncillaryDataSet>
        </SequenceContainer>

   Default: true



Compatibility
-------------

Yamcs does not seek full compliance with XTCE. It only reads the parts that relate to concepts in its internal Mission Database. This chapter presents an overview of the unsupported features and details where the implementation differs from the standard.

Note that when reading the XML XTCE file Yamcs is on purpose tolerant, it ignores the tags it does not know and it also strives to be backward compatible with XTCE 1.0 and 1.1. Thus the fact that an XML file loads in Yamcs does not mean that is 100% valid. Please use a generic XML validation tool or the `xtcetools <https://gitlab.com/dovereem/xtcetools>`_ project to validate your XML file.

The following concepts are *not supported*:

* ``Stream`` - data is assumed to be injected into Yamcs as packets, any stream processing has to be done as part of the data link definition and is not based on XTCE.
* ``Message``
* ``ParameterSegmentRefEntry``
* ``ContainerSegmentRefEntry``
* ``DiscreteLookupList``
* ``ErrorDetectCorrectType``. Note that error detection/correction is implemented directly into the Yamcs data links.
* ``ContextSignificanceList``
* ``ParameterToSetList``
* ``ParameterToSuspendAlarmsOnSet``
* ``RestrictionCriteria/NextContainer``

The other elements are supported one way or another, exceptions or changes from the specs are given in the sections below.


.. rubric:: Header

* Only the version and date are supported. ``AuthorSet`` and ``NoteSet`` are ignored.


.. rubric:: Data Encodings

* | changeThreshold
  | Not supported.

* | FromBinaryTransformAlgorithm
  | In XTCE the ``FromBinaryTransformAlgorithm`` can be specified for the ``BinaryDataEncoding``. It is not clear how exactly that is supposed to work. In Yamcs the ``FromBinaryTransformAlgorithm`` can be specified on any ``XyzDataEncoding`` and is used to convert from binary to the raw value which is supposed to be of type Xyz.

* | ToBinaryTransformAlgorithm
  | not supported for any data encoding


* | FloatDataEncoding
  | Yamcs supports IEEE754_1985, MILSTD_1750A and STRING encoding. STRING is not part of XTCE - if used, a StringDataEncoding can be attached to the FloatDataEncoding and the string will be extracted according to the StringDataEncoding and then parsed into a float or double according to the sizeInBits of FloatDataEncoding. DEC, IBM and TI encoding are not supported.

* | StringDataEncoding
  | For variable size strings whose size is encoded in front of the string, Yamcs allows to specify only for command arguments sizeInBitsOfSizeTag = 0. This means that the value of the argument will be inserted without providing the information about its size. The receiver has to know how to derive the size. This has been implemented for compatibility with other systems (e.g. SCOS-2k) which allows this - however it is not allowed by XTCE which enforces sizeInBitsOfSizeTag > 0. 


.. rubric:: Data Types

* | ValidRangeSet
  | Introduced in XTCE 1.2 for command arguments. Yamcs only supports one range in the set.

* | BooleanDataType
  | In XTCE, each ``BooleanDataType`` has a string representation. In Yamcs the value is mapped to a org.yacms.parameter.BooleanValue or the protobuf equivalent that is a wrapper for a boolean (either true or false in all sane programming languages). The string value is nevertheless supported in comparisons and math algorithms but they are converted internally to the boolean value. If you want to get to the string representation from the client, use an ``EnumeratedParameterType``.

* | RelativeTimeDataType
  | Not supported.


.. rubric:: Monitoring

* | ParameterSetType
  | ``parameterRef`` is not supported. According to XTCE doc this is "Used to include a Parameter defined in another sub-system in this sub-system". It is not clear what it means "to include". Parameters from other space systems can be referenced using a fully qualified name or a relative name.

* | ParameterProperties
  | ``PhysicalAddressSet``, ``SystemName`` and ``TimeAssociation`` are not supported.

* | Containers
  | ``BinaryEncoding`` not supported in the container definitions.

* | StringParameterType
  | Alarms are not supported.


.. rubric:: Commanding

* Arrays are not supported for commands (they are for telemetry).
* | ArgumentRefEntry
  | ``IncludeCondition`` and ``RepeatEntry`` are not supported.

* | Multiple CompleteVerifiers can be declared but the success of any of them will make the command complete successfully; XTCE specifies that all of them  have to succeed for the command to be declared successful. 
  | Note that when a command is completed (with success or failure), all the pending verifies are canceled. This means that if multiple CompleteVerifiers are declared, the first one finishing will decide the outcome of the command.


.. rubric:: Algorithms

* ``OnContainerUpdateTrigger`` is not supported.
