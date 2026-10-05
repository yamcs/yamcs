# PUS over CCSDS frames

This example carries the same PUS simulator as the [`pus`](../pus) example, but over **CCSDS
transfer frames** instead of a raw TCP packet link:

- **Telemetry**: CCSDS TM transfer frames (CCSDS 132.0-B), one virtual channel (VC 0), OCF
  (CLCW) present, CRC-16 frame error control. Received by `org.yamcs.tctm.ccsds.UdpTmFrameLink`
  on UDP port 10017.
- **Telecommand**: COP-1 / FOP-1 with BCH-encoded CLTUs, sent by
  `org.yamcs.tctm.ccsds.UdpTcFrameLink` on UDP port 10018. The FARM side lives in the simulator
  and its CLCW is reported back in every TM frame OCF, closing the COP-1 loop.

The main reason this example exists is **time correlation**. The simulator on-board clock is a
free-running clock; the ground recovers UTC by correlating the on-board time reported in the
ST[9] time packets against the *earth reception time* of the frame that carried them:

- the TM frame link publishes each good frame (with its reception time) on `good_frame_stream`;
- `PusPacketPreprocessor` is configured with `performTimeCorrelation: true` and
  `goodFrameStream: good_frame_stream`; for every ST[9] time packet it feeds an
  `(on-board time, earth reception time)` sample to the `tco0` `TimeCorrelationService`;
- after `numSamples` (4) samples `tco0` computes the correlation and from then on stamps every
  packet's generation time from it. Before that (and whenever the simulator restarts and its
  clock jumps back) it falls back to `earth reception time - delays` and re-correlates.
- `saveCoefficients: false` keeps each run independent, since the simulator clock restarts with
  the simulator.

The `PusCommandPostprocessor` also uses `tco0` here: when a command is scheduled with the
`pus11ScheduleAt` option, the TC[11,4] release time is converted from UTC to on-board time using
the correlation.

## Running

```
./run-example.sh pus-frames
```

Then, once a correlation has been established (watch the events / the `tco0` status), try the PUS
11 scheduling test:

```
python3 examples/pus-frames/tests/test-pus11.py
```

## Notes

- This example demonstrates ST[11] (time-based scheduling). The [`pus`](../pus) example
  demonstrates ST[22] (position-based scheduling) instead 
- The simulator sends TM frames at 10 Hz; if it has no data it sends idle frames.
