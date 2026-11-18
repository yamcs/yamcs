PUS Event Decoder
=================

The :javadoc:`org.yamcs.pus.PusEventDecoder` service converts PUS TM[5,1-4]
reports into Yamcs events using MDB extraction and a YAML or JSON template file.

.. code-block:: yaml

    services:
      - class: org.yamcs.pus.PusEventDecoder
        args:
          eventIdParameter: /PUS5/event_id
          eventTemplateFile: etc/event-templates.yaml
          realtimeStreams: [tm_realtime]

The event ID parameter must be enumerated or integer. Enumerations are matched
by their engineering label; integer parameters are matched by their decimal code.
The template file contains a list:

.. code-block:: yaml

    - eventId: 1234
      template: "Temperature limit exceeded"
      subsystem: FSW
    - eventId: EVENT_READY
      template: "Application ready"
    - eventId: EVENT_READY-7
      template: "Application ready on APID 7"

``eventId`` accepts a string or integer. Unquoted integers are normalized to decimal
lookup keys, so ``1234`` and ``"1234"`` match the same integer event ID. Boolean,
floating-point, null, list and map IDs are rejected with a configuration error.
``template`` must be a string; ``subsystem`` is an optional string.
An ``<eventId>-<apid>`` entry takes precedence over the generic ``<eventId>`` entry.
Message placeholders use ``{parameter_name}`` or ``{parameter_name; format}``,
with ``.raw`` selecting the parameter's raw value.

Event identity and subsystem labels
-----------------------------------

The event source is always ``APID_<number>``. The event type is the decoded event
ID, and the original generation time and sequence number are retained. An optional
subsystem label is stored in ``event.extra["subsystem"]``. Thus two APIDs using the
same subsystem and event ID remain distinct for alarm and archive identity.

Previously, a subsystem label replaced the APID source. Update source-based alarm
rules and filters that used that label to use ``APID_<number>``. Existing archived
events are not rewritten; new events carry the subsystem as extra metadata.
