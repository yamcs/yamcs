import { DySample } from './dygraphs';

export type WatermarkObserver = () => void;

export type DyValueRange = [number | null, number | null];

export interface PlotData {
  valueRange: DyValueRange;
  samples: DySample[];
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

  public dirty = false;

  private valueRange: DyValueRange = [null, null];

  private archiveSamples: DySample[] = [];

  private realtimeBuffer: (DySample | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.realtimeBuffer = Array(this.bufferSize).fill(undefined);
  }

  setArchiveData(samples: DySample[]) {
    this.archiveSamples = samples;
    this.dirty = true;
  }

  setValueRange(valueRange: DyValueRange) {
    this.valueRange = valueRange;
  }

  addRealtimeValue(sample: DySample) {
    if (this.pointer < this.bufferSize) {
      this.realtimeBuffer[this.pointer] = sample;
      if (this.pointer >= this.bufferWatermark && this.watermarkObserver && !this.alreadyWarned) {
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

  snapshot(): PlotData {
    const realtimeSamples = this.realtimeBuffer.filter(s => s !== undefined) as DySample[];

    // Archive sample data contains [null] points for future data (because of empty buckets)
    // Filter these out so that they don't overlap with incoming realtime.
    let archiveCutOff: number | null = null;
    if (realtimeSamples.length > 0) {
      archiveCutOff = realtimeSamples[0][0].getTime();
    }

    const splicedSamples = this.archiveSamples
      .filter(s => archiveCutOff === null || s[0].getTime() < archiveCutOff)
      .concat(realtimeSamples)
      .sort((s1, s2) => s1[0].getTime() - s2[0].getTime());
    return {
      valueRange: this.valueRange,
      samples: splicedSamples,
    };
  }
}
