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

  // Width of a sample interval in milliseconds.
  // Zero (the initial value) disables merging.
  private bucketMs = 0;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.buffer = Array(this.bufferSize).fill(undefined);
  }

  setBucketSize(bucketMs: number) {
    this.bucketMs = Math.max(0, bucketMs);
  }

  push(point: PlotPoint) {
    const last = this.pointer > 0 ? this.buffer[this.pointer - 1]! : undefined;
    if (last && this.bucketMs > 0 && canMerge(last, point, this.bucketMs)) {
      last.firstTime = Math.min(last.firstTime, point.firstTime);
      last.lastTime = Math.max(last.lastTime, point.lastTime);
      if (last.avg !== null && point.avg !== null) {
        last.avg =
          (last.avg * last.n + point.avg * point.n) / (last.n + point.n);
        last.min = Math.min(last.min!, point.min!);
        last.max = Math.max(last.max!, point.max!);
      }
      last.n += point.n;
      return;
    }

    if (this.pointer < this.bufferSize) {
      this.buffer[this.pointer] = {
        ...point,
        // Align with the archive convention: the time of a point is the
        // start of its sample interval
        time:
          this.bucketMs > 0
            ? point.time - (point.time % this.bucketMs)
            : point.time,
      };
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
 * A value can only be merged into the preceding point if it falls within the
 * same sample interval, and if it does not change the gap/no-gap status of
 * that point. A gap therefore always starts a new point, and so does the
 * first value after a gap.
 */
function canMerge(last: PlotPoint, point: PlotPoint, bucketMs: number) {
  if (point.time < last.time || point.time >= last.time + bucketMs) {
    return false;
  }
  return (last.avg === null) === (point.avg === null);
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
  private bucketMs = 0;

  constructor(private watermarkObserver: WatermarkObserver) {}

  /**
   * Sets the width of the sample interval in which incoming realtime values
   * are merged. This should match the bucket width of the archive samples, so
   * that the size of the realtime buffers, and therefore how often the
   * watermark observer is triggered to reload the archive samples, depends on
   * the plotted time range rather than on the update rate of the parameters.
   */
  setBucketSize(bucketMs: number) {
    this.bucketMs = bucketMs;
    this.realtimeRawBuffers.forEach((b) => b.setBucketSize(bucketMs));
    this.realtimeEngBuffers.forEach((b) => b.setBucketSize(bucketMs));
  }

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
      realtimeRawBuffer.setBucketSize(this.bucketMs);
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
      realtimeEngBuffer.setBucketSize(this.bucketMs);
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
