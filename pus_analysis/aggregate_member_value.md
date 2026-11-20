# AggregateMemberValue — aggregate-scoped TM dimensions and binary sizes

## Names and purpose

“AggregateMemberValue” describes the value used to size an array or binary field from a member
of the aggregate currently being decoded. In Yamcs, the relevant XML and Java names are:

| Layer | Name |
|---|---|
| XML metadata on `ArrayParameterType` or `BinaryParameterType` | `AncillaryData name="Yamcs:AggregateMember"` |
| Dynamic reference in `DimensionList` or `SizeInBits` | `ParameterInstanceRef parameterRef="count"` |
| Parsed Java reference | `org.yamcs.xtce.AggregateMemberInstanceRef` |
| Dynamic dimension | `org.yamcs.xtce.DynamicIntegerValue` |
| Extracted aggregate / member value | `org.yamcs.parameter.AggregateValue` / `Value` |

This Yamcs convention permits nested, variable-length TM arrays to be returned as one
structured parameter. Each array element can have its own count. It does not change any
packet bytes. The XML uses schema-permitted ancillary data; the interpretation is Yamcs-specific.

## Complete minimal TM example

This database decodes one report containing `count:u8` followed by `count` unsigned bytes:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<SpaceSystem xmlns="http://www.omg.org/spec/XTCE/20180204" name="MemberCountExample">
  <TelemetryMetaData>
    <ParameterTypeSet>
      <IntegerParameterType name="uint8" signed="false">
        <IntegerDataEncoding sizeInBits="8" encoding="unsigned"/>
      </IntegerParameterType>
      <ArrayParameterType name="values_type" arrayTypeRef="uint8">
        <AncillaryDataSet>
          <AncillaryData name="Yamcs:AggregateMember">count</AncillaryData>
        </AncillaryDataSet>
        <DimensionList>
          <Dimension>
            <StartingIndex><FixedValue>0</FixedValue></StartingIndex>
            <EndingIndex>
              <DynamicValue>
                <ParameterInstanceRef parameterRef="count" useCalibratedValue="false"/>
                <LinearAdjustment intercept="-1"/>
              </DynamicValue>
            </EndingIndex>
          </Dimension>
        </DimensionList>
      </ArrayParameterType>
      <AggregateParameterType name="report_type">
        <MemberList>
          <Member name="count" typeRef="uint8"/>
          <Member name="values" typeRef="values_type"/>
        </MemberList>
      </AggregateParameterType>
    </ParameterTypeSet>
    <ParameterSet>
      <Parameter name="report" parameterTypeRef="report_type"/>
    </ParameterSet>
    <ContainerSet>
      <SequenceContainer name="packet">
        <EntryList><ParameterRefEntry parameterRef="report"/></EntryList>
      </SequenceContainer>
    </ContainerSet>
  </TelemetryMetaData>
</SpaceSystem>
```

Wire bytes `03 11 22 33` produce `report = {count: 3, values: [17, 34, 51]}`.
Wire byte `00` produces an empty `values` array and consumes no element bytes.
The count is an ordinary aggregate member, not a separately declared MDB parameter.

`EndingIndex` is inclusive: for a zero-based array with N elements use N−1. The reader
converts the index range into a size, so the stored dynamic size is N. Omitting
`LinearAdjustment intercept="-1"` makes the array one element too long.

Put `AncillaryDataSet` before `DimensionList`: the reader collects the marked names before
parsing the dimensions. Repeat the ancillary entry for each distinct count member used by a
multidimensional array. The metadata belongs on the **array parameter type**, not on the
aggregate, its member, or the `ParameterInstanceRef` itself.

## Resolution and validation

1. `XtceStaxReader` matches the dynamic size's `parameterRef` against the marked names and creates
   an `AggregateMemberInstanceRef`. An unmarked reference remains an ordinary MDB parameter
   reference; a bare name does not automatically mean a sibling member.
2. `AggregateMemberReferenceValidator` walks every concrete parameter type use site at MDB
   load time. A reusable type must have a valid size reference in every enclosing context.
3. `SequenceEntryProcessor` keeps a stack of aggregates during extraction. Lookup starts at
   the innermost aggregate and continues outwards. Each repeated aggregate has its own value,
   so an inner count is resolved again for each element.
4. The nearest member with that exact name wins. It shadows outer members even if declared
   too late; lookup does not silently fall back to an outer count.
5. The count member must be declared before the dependent field and must be integer-compatible in the
   selected raw/calibrated representation. Missing, late, noninteger, or circular type uses
   fail database loading.

`useCalibratedValue` defaults to `true`. With that setting the count's engineering type must
be an integer type. With `false`, its raw encoding must be `IntegerDataEncoding`. For a wire
count, `false` usually expresses the intended meaning directly. Enumeration labels are not
integer engineering values; use a suitable raw encoding when counting with an enumerated field.

Only `instance="0"` (also the default) is accepted. This is lexical aggregate lookup, not
historical parameter-instance selection. Use a simple member name; qualified parameter paths
and dotted member paths are not traversed by this lookup.

Negative counts, arithmetic overflow, and dimensions/products exceeding the extractor's
`maxArraySize` produce processing errors. Zero counts are valid. These checks do not make a
malformed packet successfully decode; inspect the extraction error rather than assuming a
complete report was returned.

## Nested arrays and alternatives

For `{outer_count, entries: [{inner_count, values: [...]}]}`, mark `outer_count` on the
`entries` array type and `inner_count` on the `values` array type. The result remains one
aggregate parameter with nested arrays, including empty arrays at either level. See the
complete [nested TM fixture](../yamcs-core/src/test/resources/xtce/array-in-array-tm.xml).

For binary fields, put the marker on `BinaryParameterType` before `BinaryDataEncoding`, then
reference the marked member from `SizeInBits`. For example, TM[11,10] can decode each embedded
TC as a CCSDS header aggregate followed by its own `packet_data`. The CCSDS length word is one
less than the number of data bytes, so the binary size adjustment uses `slope="8"` and
`intercept="8"`. The extractor resolves the length separately for each activity.

For an already completed top-level aggregate, a normal reference such as
`parameterRef="configuration.count"` is a different mechanism: it identifies an MDB parameter
and a member path. It does not refer to an in-progress repeated aggregate.

`ContainerRefEntry` + `RepeatEntry` is another representation of repeated TM fields. Ordinary
`DynamicValue` references now use `CURRENT_ENTRY_WITHIN_PACKET` when `instance` is omitted,
so each count or binary length uses the most recently decoded occurrence. Explicit
`instance="0"` retains packet-start lookup of the first occurrence; positive explicit instances
select later occurrences from packet start. The exporter retains an explicit zero to preserve
that distinction. Other reference contexts retain their existing rules.

This differs from aggregate-member lookup: repeated containers yield repeated parameter
values, while aggregate-member dimensions preserve a nested structured value. The PUS example
MDBs still use repeated containers. See the [review findings](README.md#inconsistencies-and-current-limitations).

On the **TC side**, nested arrays use sibling-member `ArgumentInstanceRef`, resolved through
`TcProcessingContext`'s aggregate stack. Do not copy the TM ancillary marker onto an
`ArrayArgumentType`; the TM reader convention and TC argument lookup are separate mechanisms.

## Legacy XML and export

The reader still imports the older namespaced marker on a dimension reference:

```xml
<ParameterInstanceRef xmlns:yamcs="http://yamcs.org/schema/xtce"
    parameterRef="count" yamcs:aggregateMember="true"/>
```

Use the ancillary-data representation for new definitions. `XtceAssembler` exports marked
references as `Yamcs:AggregateMember` ancillary entries, including after legacy import. The
source schedules the attribute reader for deprecation but gives no removal release or date.
The former attribute is not the schema-valid representation used by current exports.

## Source and regression coverage

| Source | What it establishes |
|---|---|
| [AncillaryData](../yamcs-xtce/src/main/java/org/yamcs/xtce/AncillaryData.java) | Marker constant and migration guidance |
| [AggregateMemberInstanceRef](../yamcs-xtce/src/main/java/org/yamcs/xtce/AggregateMemberInstanceRef.java) | Lexical reference model |
| [XtceStaxReader](../yamcs-xtce/src/main/java/org/yamcs/xtce/xml/XtceStaxReader.java) | Metadata ordering, instance restriction, legacy import, ordinary-reference policy |
| [AggregateMemberReferenceValidator](../yamcs-core/src/main/java/org/yamcs/mdb/AggregateMemberReferenceValidator.java) | Per-use-site validation, ordering, shadowing, integer compatibility |
| [SequenceEntryProcessor](../yamcs-core/src/main/java/org/yamcs/mdb/SequenceEntryProcessor.java) | Runtime lookup, calibration, dimensions and limits |
| [ArrayInArrayTmTest](../yamcs-core/src/test/java/org/yamcs/mdb/ArrayInArrayTmTest.java) | Unequal nested counts, zero counts, completed aggregate paths, packed PUS14-shaped hierarchy |
| [AggregateBinaryTmTest](../yamcs-core/src/test/java/org/yamcs/mdb/AggregateBinaryTmTest.java) | Per-row binary sizes, XTCE export and re-import, and binary extraction after a packed flag |
| [AggregateMemberReferenceValidatorTest](../yamcs-core/src/test/java/org/yamcs/mdb/AggregateMemberReferenceValidatorTest.java) | Reuse, enclosing scopes, missing/late counts, shadowing, noninteger rejection |
| [XtceAssemblerTest](../yamcs-core/src/test/java/org/yamcs/mdb/XtceAssemblerTest.java) | Round-trip, schema validity and legacy migration |

Run the existing focused tests from the repository root:

```bash
mvn -pl yamcs-core -am -Dtest=ArrayInArrayTmTest,AggregateBinaryTmTest,AggregateMemberReferenceValidatorTest,XtceAssemblerTest -Dsurefire.failIfNoSpecifiedTests=false test
```
