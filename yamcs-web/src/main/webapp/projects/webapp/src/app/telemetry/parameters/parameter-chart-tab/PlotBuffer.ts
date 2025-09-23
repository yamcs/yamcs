import { PlotPoint } from './PlotPoint';
import { ValueType } from './TraceConfig';

export type WatermarkObserver = () => void;

export interface NamedSeries {
  traceId: string;
  name: string;
  valueType: ValueType;
  series: PlotPoint[];
}

export type PlotData = {
  traceId: string;
  points: PlotPoint[];
};

/**
 * Buffer for a single parameter's realtime values.
 *
 * This merges incoming values together in a single
 * data point (~ equivalent of an archive sample),
 * except when gaps are detected.
 */
class RealtimeBuffer {
  private buffer: (PlotPoint | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.buffer = Array(this.bufferSize).fill(undefined);
  }

  push(point: PlotPoint) {
    if (this.pointer < this.bufferSize) {
      this.buffer[this.pointer] = point;
      if (
        this.pointer >= this.bufferWatermark &&
        this.watermarkObserver &&
        !this.alreadyWarned
      ) {
        this.watermarkObserver();
        this.alreadyWarned = true;
      }
      this.pointer = this.pointer + 1;
    }
  }

  snapshot() {
    return this.buffer.filter((s) => s !== undefined);
  }

  reset() {
    this.buffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
  }
}

/**
 * Combines archive samples obtained via REST
 * with realtime samples obtained via WebSocket.
 *
 * This class does not care about whether archive samples
 * and realtime values are connected. Both sets are joined
 * and sorted under all conditions.
 */
export class PlotBuffer {
  private archiveDataById = new Map<string, NamedSeries>();
  private realtimeRawBuffers = new Map<string, RealtimeBuffer>();
  private realtimeEngBuffers = new Map<string, RealtimeBuffer>();

  constructor(private watermarkObserver: WatermarkObserver) {}

  setArchiveData(archiveData: NamedSeries[]) {
    this.archiveDataById.clear();
    for (const series of archiveData) {
      this.archiveDataById.set(series.traceId, series);
    }
  }

  clearArchiveData(traceId: string) {
    for (const [k, v] of this.archiveDataById) {
      if (k === traceId) {
        v.series.length = 0;
        break;
      }
    }
  }

  addRealtimeValue(
    seriesName: string,
    time: number,
    rawValue: number | null,
    engValue: number | null,
  ) {
    let realtimeRawBuffer = this.realtimeRawBuffers.get(seriesName);
    if (!realtimeRawBuffer) {
      realtimeRawBuffer = new RealtimeBuffer(this.watermarkObserver);
      this.realtimeRawBuffers.set(seriesName, realtimeRawBuffer);
    }
    realtimeRawBuffer.push({
      time,
      firstTime: time,
      lastTime: time,
      n: 1,
      avg: rawValue,
      min: rawValue,
      max: rawValue,
    });

    let realtimeEngBuffer = this.realtimeEngBuffers.get(seriesName);
    if (!realtimeEngBuffer) {
      realtimeEngBuffer = new RealtimeBuffer(this.watermarkObserver);
      this.realtimeEngBuffers.set(seriesName, realtimeEngBuffer);
    }
    realtimeEngBuffer.push({
      time,
      firstTime: time,
      lastTime: time,
      n: 1,
      avg: engValue,
      min: engValue,
      max: engValue,
    });
  }

  reset() {
    this.archiveDataById.clear();
    this.realtimeRawBuffers.forEach((b) => b.reset());
    this.realtimeEngBuffers.forEach((b) => b.reset());
  }

  snapshot(start: number, stop: number): PlotData[] {
    const plotData: PlotData[] = [];

    for (const [traceId, namedSeries] of this.archiveDataById) {
      const { name: qualifiedName, valueType } = namedSeries;
      const realtimeBuffer =
        valueType === 'raw'
          ? this.realtimeRawBuffers.get(qualifiedName)?.snapshot() || []
          : this.realtimeEngBuffers.get(qualifiedName)?.snapshot() || [];
      const realtimePoints = realtimeBuffer.filter((s) => s !== undefined);

      // Archive sample data contains [null] points for future data (because of empty buckets)
      // Filter these out so that they don't overlap with incoming realtime.
      let archiveCutOff: number | null = null;
      if (realtimePoints.length > 0) {
        archiveCutOff = realtimePoints[0].time;
      }

      let splicedPoints = namedSeries.series.filter(
        (s) => archiveCutOff === null || s.time < archiveCutOff,
      );

      // Ignore realtime points if they fall outside of the visible
      // viewport (this avoids seeing a straight line between the
      // archive tail and realtime head when panning).
      if (realtimePoints.length > 0) {
        if (realtimePoints[0].time <= stop) {
          splicedPoints = splicedPoints.concat(realtimePoints);
        }
      }

      splicedPoints = splicedPoints.sort((s1, s2) => s1.time - s2.time);
      plotData.push({ traceId, points: splicedPoints });
    }
    return plotData;
  }
}
