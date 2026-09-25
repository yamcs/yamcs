import { CustomBarsValue, DySample } from './DySample';

export type WatermarkObserver = () => void;

export type DyValueRange = [number | null, number | null];

export interface DyPlotData {
  valueRange: DyValueRange;
  samples: DySample[];
}

/**
 * Running min/avg/max of the realtime values that fell in one bucket
 */
interface BucketStats {
  min: number;
  max: number;
  sum: number;
  n: number;
}

/**
 * Realtime values aggregated per time bucket.
 * One entry per plotted parameter (null represents a gap).
 */
interface RealtimeBucket {
  start: number; // inclusive
  stop: number; // exclusive
  time: Date; // Time of the first value, used for plotting
  columns: (BucketStats | null)[];
}

/**
 * Combines archive samples obtained via REST
 * with realtime samples obtained via WebSocket.
 *
 * This class does not care about whether archive samples
 * and realtime values are connected. Both sets are joined
 * and sorted under all conditions.
 *
 * Realtime values are downsampled into buckets of the same width as
 * the archive samples (see {@link setBucketSize}), so that the size of the
 * realtime buffer, and therefore how often the watermark observer is
 * triggered to reload the archive samples, depends on the plotted time
 * range rather than on the update rate of the parameter. Without this,
 * a 100 Hz parameter would trigger a reload every few seconds.
 */
export class DyPlotBuffer {
  public dirty = false;

  private valueRange: DyValueRange = [null, null];

  private archiveSamples: DySample[] = [];

  private realtimeBuffer: (RealtimeBucket | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  // Width of a realtime bucket in milliseconds.
  // Zero (the initial value) disables aggregation.
  private bucketMs = 0;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.realtimeBuffer = Array(this.bufferSize).fill(undefined);
  }

  setArchiveData(samples: DySample[]) {
    this.archiveSamples = samples ?? [];
    this.dirty = true;
  }

  setValueRange(valueRange: DyValueRange) {
    this.valueRange = valueRange;
  }

  /**
   * Sets the width of the buckets in which incoming realtime values
   * are aggregated. This should match the bucket width of the
   * archive samples.
   */
  setBucketSize(bucketMs: number) {
    this.bucketMs = Math.max(0, bucketMs);
  }

  addRealtimeValue(sample: DySample) {
    const t = sample[0].getTime();
    const values = sample.slice(1) as CustomBarsValue[];

    const last =
      this.pointer > 0 ? this.realtimeBuffer[this.pointer - 1] : undefined;
    if (last && t >= last.start && t < last.stop && canMerge(last, values)) {
      for (let i = 0; i < values.length; i++) {
        const stats = last.columns[i];
        const value = values[i];
        if (stats && value) {
          stats.min = Math.min(stats.min, value[0]);
          stats.max = Math.max(stats.max, value[2]);
          stats.sum += value[1];
          stats.n++;
        }
      }
      this.dirty = true;
      return;
    }

    if (this.pointer < this.bufferSize) {
      const start = this.bucketMs > 0 ? t - (t % this.bucketMs) : t;
      this.realtimeBuffer[this.pointer] = {
        start,
        stop: start + this.bucketMs,
        time: sample[0],
        columns: values.map((value) =>
          value ? { min: value[0], max: value[2], sum: value[1], n: 1 } : null,
        ),
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
    this.dirty = true;
  }

  reset() {
    this.archiveSamples = [];
    this.realtimeBuffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
    this.valueRange = [null, null];
    this.dirty = true;
  }

  snapshot(): DyPlotData {
    const realtimeSamples: DySample[] = [];
    for (let i = 0; i < this.pointer; i++) {
      const bucket = this.realtimeBuffer[i]!;
      const columns = bucket.columns.map((stats) =>
        stats ? [stats.min, stats.sum / stats.n, stats.max] : null,
      ) as CustomBarsValue[];
      realtimeSamples.push([bucket.time, ...columns] as DySample);
    }

    // Archive sample data contains [null] points for future data (because of empty buckets)
    // Filter these out so that they don't overlap with incoming realtime.
    let archiveCutOff: number | null = null;
    if (realtimeSamples.length > 0) {
      archiveCutOff = realtimeSamples[0][0].getTime();
    }

    const splicedSamples = this.archiveSamples
      .filter((s) => archiveCutOff === null || s[0].getTime() < archiveCutOff)
      .concat(realtimeSamples)
      .sort((s1, s2) => s1[0].getTime() - s2[0].getTime());
    return {
      valueRange: this.valueRange,
      samples: splicedSamples,
    };
  }
}

/**
 * A value can only be merged into a bucket if it does not change
 * the gap/no-gap status of any column. A gap (null) therefore always
 * starts a new bucket, and so does the first value after a gap.
 */
function canMerge(bucket: RealtimeBucket, values: CustomBarsValue[]) {
  if (bucket.columns.length !== values.length) {
    return false;
  }
  for (let i = 0; i < values.length; i++) {
    if ((bucket.columns[i] === null) !== (values[i] === null)) {
      return false;
    }
  }
  return true;
}
