# PUS ST[03] — Housekeeping and diagnostic reporting

Yamcs encodes structure commands and decodes reports. The simulator owns collection,
structure registries, reporting configuration, and periodic generation.

## Current artifacts and coverage

| Source | Role |
|---|---|
| [pus3.xml](../examples/pus/src/main/yamcs/mdb/pus3.xml) | Structure/configuration commands and reports |
| [pus.xml](../examples/pus/src/main/yamcs/mdb/pus.xml) | `/PUS/hkid` and common HK container |
| [landing.xml](../examples/pus/src/main/yamcs/mdb/landing.xml) | Predefined HK payload decoding |
| [Pus3Service](../simulator/src/main/java/org/yamcs/simulator/pus/Pus3Service.java) | Simulated structure state and periodic reports |

The simulator handles housekeeping, diagnostic, and functional-reporting configuration
subtypes. “TC[3,1–44]” is a range shorthand, not a statement that every integer is a TC subtype.

| TC group | Purpose | Associated TM |
|---|---|---|
| 1/2, 3/4 | Create/delete HK/diagnostic structures | 10/12 structure reports on request |
| 5–8 | Enable/disable periodic HK/diagnostic generation | 25/26 parameter reports |
| 9/11 | Report HK/diagnostic structures | 10/12 |
| 27/28 | One-shot generation | 25/26 |
| 29/30 | Append parameters | — |
| 31/32 | Change collection interval | — |
| 33/34 | Report periodic properties | 35/36 |
| 37–40, 42–44 | Apply/create/delete/report/modify functional reporting definitions | 41 |

## Mission conventions and predefined reports

Structure IDs are unsigned 8-bit values and parameter IDs are unsigned 16-bit values.
Functional reporting definition IDs and collection intervals are unsigned 32-bit values.
Collection intervals are expressed in milliseconds. TM[3,10/12] describes the structure
with its SID, periodic status, collection interval, simple PID list, and super-commutated
sets. Each super-commutated set carries its own repetition and PID count.

TM[3,25/26] source data begins with a one-byte SID. In the main example, `/PUS/hkid` is at
offset 25 from the beginning of the packet, after the explicit CUC time and four spare
bytes. In jTYU's implicit CUC profile, the source-data offset is 23.

Predefined simulator structures 0–4 correspond to the FlightData, Power, DHS, RCS and EPS
handlers. Structure 5 carries enum-chart test parameters; structure 6 carries ST[22] orbit
position. These are the actual example structures, rather than proposed power-bus/ADCS
structures with a different ID scheme. Their payloads are decoded by the loaded MDB.

[ST[130]](pus130.md) can activate periodic generation for claimed SIDs and apply the active
telemetry mode. Storage and forwarding rules use the same one-byte SID.

## Dynamic structure limitation

TM[3,25/26] data types and ordering are deduced from the report structure, rather than carried
as a self-describing field list in each packet. Static XTCE definitions decode the predefined
structures. The simulator can accept structure creation and modification commands, but this
does not dynamically rewrite the ground MDB or install decoding containers for arbitrary new
layouts.

There is no `Pus3HkDecoder` class in this checkout. A live structure registry and dynamic TM
decoder would be additional ground functionality. A usable design would need to obtain the
confirmed on-board structure definitions, map parameter IDs to known wire types and retain
acquisition/generation times while integrating with the parameter pipeline. Sending a create
TC alone is not proof that the structure exists on board. Persistence in `MementoDb`, if
added, would also need reconciliation with spacecraft state.

[Aggregate member dimensions](aggregate_member_value.md) support nested array counts within
known types. They do not discover an unknown parameter schema or solve dynamic HK structure
discovery. Repeated structure-report containers also need their ordinary XML count/instance
semantics checked for heterogeneous super-commutated sets.

## Manual verification and remaining boundaries

Start the [example](pus_native_arch.md#build-and-verification). Check predefined HK containers,
then issue enable/disable, interval change and one-shot commands using their exact argument
names in `pus3.xml`. Confirm command verification and the corresponding report separately.
Exercise zero and multiple IDs, unknown structure IDs, and deleting an enabled structure.
For structure reports, vary inner counts rather than testing only equal-sized sets.

Functional-reporting definitions are simulator state, not a ground-side persistent mirror.
Super-commutated value ordering and type interpretation must match the actual structure;
flat repetition alone does not establish correct time-tagging of each sample. Arbitrary
new HK schemas, restart reconciliation and mission-specific optional fields remain beyond
the static example's automatic ground support.
