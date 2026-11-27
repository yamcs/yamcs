# Yamcs PUS analysis — review and navigation

The guides describe the current PUS simulator and Yamcs ground configuration. The review
table records earlier findings and links to the active implementation. The packet contract
and direct UDP profile are in the [simulator architecture](pus_simulator_architecture.md)
and [ground architecture](pus_native_arch.md).

This folder documents Yamcs ground behavior, its example XTCE MDBs and its Java spacecraft
simulator. External deployment assumptions, unrelated MDB-builder examples, private plan
references and standalone simulator drafts have been removed. ECSS service terminology is
retained for context. Standard section references in research/proposals are not an independent
conformance assessment; widths and optional capabilities require a mission ICD.

## Inconsistencies and current limitations

“Corrected” means documentation was changed. “Open” means implementation work remains.
The follow-up dynamic-reference fix also changes the XTCE reader/exporter, invalidates old MDB
caches and adds decoding/export regression tests; its status is identified below.

| Priority | Finding | Evidence / correction | Status |
|---|---|---|---|
| High | The architecture guide configured removed core on-board handlers, while service guides called Yamcs ground-only. | Reviewed core package has no `PusCommandReleaser`, `PusTcHandler`, core ST[05]/ST[09]/ST[21] handler. Example `processor.yaml` uses `StreamTcCommandReleaser`. See [ground architecture](pus_native_arch.md). | Corrected for baseline |
| High | Dynamic lengths/counts reused the first occurrence in repeated entries. | Fixed in the working tree: `DynamicValue` uses the latest decoded occurrence when `instance` is omitted; explicit non-negative instances retain packet-start indexing. Export preserves explicit zero, dynamic binary sizes and referenced child containers. Old MDB caches are invalidated. Tests cover unequal counts/lengths, empty arrays, explicit selection, export/reload and the actual ST[19] MDB. | Fixed with regression coverage |
| High | The guides said TM arrays cannot use counts inside their enclosing aggregates. | Current `Yamcs:AggregateMember` ancillary data creates `AggregateMemberInstanceRef`; validator/extractor support lexical scopes and variable nested counts. See the [AggregateMemberValue guide](aggregate_member_value.md). The PUS example MDBs have not adopted this representation. | Corrected; migration remains open |
| High | ST[11] examples used rejected top-level configuration and obsolete history-key behavior. | Use the nested `pus11` map, optional ID blocks and correctly cased command options. Generic history identifies the original TC; `pus11Apid`/`pus11CcsdsSeqCount` identify the wrapper in the reviewed baseline. See [ST[11]](pus11.md). | Corrected for baseline |
| High | ST[19] descriptions alternated between a separate request-length prefix and no prefix; examples supplied an obsolete `request_len` argument. | Current `request` uses `/PUS/EmbeddedTcType` and its own CCSDS length. Rejection is failed start per instruction, codes 30–33; capacity is 16. Annotated nested packets are postprocessed. | Corrected |
| High | ST[21] claimed a ground embedded-entry builder existed. | `Pus21EmbeddedEntryBuilder` is absent at the baseline. Its `tc_packet_arg_type` is unannotated, so embedded bytes must already be finalized. Generic embedded-TC postprocessing exists but is not enabled for that type. See [ST[21]](pus21.md). | Corrected; convenience/integration gap open |
| Medium | Packet examples used service/subtype at bytes 6/7, 8-byte empty packets, and ordinary TM offset 22. | Full PUS service/subtype are bytes 7/8. With four spare bytes, TC service data starts at 15. Ordinary TM service data starts at 25 with the explicit CUC example and 23 with the six-byte implicit CUC profile. APID-zero time starts at byte 7. | Corrected |
| Medium | ST[13] research suggested automatic trailing-binary sizing while the implementation adds a last-part length byte. | MDB and simulator encode `part_len`/`part_len_last` explicitly. That is an example-specific deviation from the research's deduced-size layout, requiring ICD agreement. | Corrected; interoperability decision open |
| Medium | ST[14] said HK reports lack structure IDs and called diagnostic reporting ST[04]. | ST[03] uses uint8 structure IDs. ST[14] consults its HK and diagnostic structure tables for TM[3,25] and TM[3,26] along with APFCC and event rules. | Aligned |
| Medium | ST[15] sections mixed raw-millisecond timestamps with CUC, a proposed flags byte, missing uint64 types and thread-per-store sketches. | `dt.xml` already contains uint64; current timestamps use simulator milliseconds; no added flags byte; retrieval uses the shared scheduled executor. Empty application-selection tables block storage. | Corrected; mission time convention remains |
| Medium | ST[12] called the simulator absent and treated TC conditional entries as supported. | Service/registration/event IDs already exist. Three typed add/modify commands are the supported encoder path. TM conditional parameter extraction is a separate feature. | Corrected |
| Medium | ST[03] described proposed IDs/containers as existing and placed a decoder at the TC offset. | Structure IDs are uint8, parameter IDs are uint16 and intervals are uint32 milliseconds; predefined reports include structures 0–6. No dynamic ground HK schema decoder is shipped. | Corrected; dynamic HK support open |
| Medium | ST[20] promised failed-start reports for unknown IDs while its walkthrough expected completion. | Current simulator skips unknown entries, applies valid ones and sends completion; reads omit unknown IDs. | Corrected; standard rejection coverage not established |
| Medium | ST[06] was presented as already solved using “validated” framing precedents. | No `pus6.xml`/`Pus6Service` exists. Retained as an explicitly unimplemented proposal, with repeated TM sizing and wire conventions unresolved, including uint32 request versus uint16 report lengths. | Corrected; implementation open |
| Low | Source paths, numerical contents-line tables, deployment-specific recommendations and old standalone transport examples obscured the current implementation. | Replaced obsolete drafts with current source-linked guides and retained useful current command walkthroughs. | Corrected |
| Low | Native event conversion was described as YAML-only and the verifier service-type input was misnamed. | `PusEventDecoder` accepts YAML/JSON, example `events.json`; verifier alias is `reportType`, not `reportServiceType`. | Corrected |

The aggregate-member feature supports array counts and binary lengths from an earlier member of an enclosing aggregate. The generated TM[11,10] MDB uses the inner packet length to size the nested binary field.

## Document map

| Document | Scope / implementation status |
|---|---|
| [Ground architecture](pus_native_arch.md) | Ground pipeline, configuration, events, verification and embedded TCs |
| [Simulator architecture](pus_simulator_architecture.md) | Actual Java dispatcher, packet layouts, storage/forwarding order and scheduling |
| [AggregateMemberValue](aggregate_member_value.md) | Complete XML example, scope/validation, calibration, legacy migration and tests |
| [ST[02]](pus02.md) | Fixed-layout physical/logical device sample |
| [ST[03]](pus03.md) | HK/diagnostic/PFRC; static ground schemas and dynamic-decoding boundary |
| [ST[05]](pus05.md) | Event reports and native Yamcs event conversion |
| [ST[06]](pus06.md) | Proposal only; not registered or implemented |
| [ST[09]](pus09.md) | Time rate control and APID-zero time reports |
| [ST[11]](pus11.md) | Ground wrapping and Java on-board scheduler |
| [ST[12]](pus12.md) | Parameter monitoring sample and live-client checks |
| [ST[13]](pus13.md) | Fragment transfer, explicit last-part length convention |
| [ST[14]](pus14.md) | APFCC, HK/diagnostic and event forwarding |
| [ST[15]](pus15.md) | Store lifecycle, selection, retrieval and raw timestamp convention |
| [ST[17]](pus17.md) | Empty-data connection test and packed target-APID echo |
| [ST[19]](pus19.md) | Event-action state, embedded TC handling and rejection semantics |
| [ST[20]](pus20.md) | Fixed uint32 parameter values and unknown-ID behavior |
| [ST[21]](pus21.md) | Sequence store, seeded file path and embedded construction limits |
| [ST[128]](pus128.md) | TLE set, report and checksum verification |
| [ST[130]](pus130.md) | TM modes, overlays and timed pass operations |
| ST[131] | ST[01] acknowledgements for the exported TC[131,4] commands |

ST[22] and an ST[23] MDB also exist in the repository but had no dedicated analysis page in
this folder. An XML file's presence alone does not establish that a service is registered or
its simulator exists.

## Current verification

The Yamcs XTCE loader accepts all three example MDB configurations and a generated MDB
using the direct UDP profile. The generated ST[03] and ST[05] simulator packets were decoded
against their matching typed MDB containers. A live Yamcs run in direct mode also archived periodic telemetry from multiple APIDs and returned
TM[130,4] in response to TC[130,3]. The direct UDP check exercised independent frame and
packet CRC settings, two-byte ST[11] IDs, and rejection of a corrupted scheduling wrapper.

## Earlier review evidence

Source inspection used the reviewed implementation, MDBs and existing regression fixtures.
The local graph was stale at review start and was not rebuilt or used as behavioral evidence.

The dynamic-reference fix is verified from a clean temporary source copy to avoid invalid
compiled Protobuf signatures in the existing checkout's build output. Eight new regression
cases cover unequal binary lengths and array counts, zero-length arrays, explicit packet-start
instances, export/reload (including dynamic binary arguments), and an actual ST[19] report
with 13-byte and 16-byte embedded requests. The original failure was observed before the fix;
removing dynamic binary-size export also makes its new command-layout regression fail.

The broader core run executed 1,249 tests: 1,234 passed, 14 were skipped and one failed:
`TmReceptionPerformanceTest.indexedPagesSeekAndReportCosts` expected 4,000 index entries but
found zero. The same failure was reproduced on the unmodified source baseline. This is a
separate existing failure, not a passing full-suite result.

The final focused reactor run passed all **65** selected tests: 39 core tests (including all
eight new regressions and existing array, binary and export coverage), plus all 26 simulator
tests for ST[11], ST[19] and ST[22]. The changed source, test fixtures and ST[19] MDB in the
clean verification copy were checked byte-for-byte against the working checkout.

Documentation checks validate local links, code fences and removal of external deployment/
harness references. The complete aggregate-member XML example passes the repository XTCE 1.2
schema with the JDK validator. Manual simulator walkthroughs remain procedures, not records
of a live simulator session.
