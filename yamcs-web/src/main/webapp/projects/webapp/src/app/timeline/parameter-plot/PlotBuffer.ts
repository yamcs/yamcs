import { PlotPoint } from './PlotPoint';

export type WatermarkObserver = () => void;

export interface NamedSeries {
  name: string;
  series: PlotPoint[];
}

export type PlotSeries = PlotPoint[];

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
  public forceNextPoint = false;

  constructor(private watermarkObserver: WatermarkObserver) {
    this.buffer = Array(this.bufferSize).fill(undefined);
  }

  push(point: PlotPoint) {
    let prev: PlotPoint | undefined = undefined;
    if (this.pointer > 0) {
      prev = this.buffer[this.pointer - 1];
    }

    // Merge with previous point when possible
    if (prev && !this.forceNextPoint) {
      if (prev.n === 0 && point.n === 0) { // Consecutive gaps
        prev.time = Math.min(prev.time, point.time);
        prev.firstTime = Math.min(prev.firstTime, point.firstTime);
        prev.lastTime = Math.max(prev.lastTime, point.lastTime);
        return;
      } else if (prev.n > 0 && point.n > 0) { // Consecutive non-gaps
        prev.time = Math.min(prev.time, point.time);
        prev.firstTime = Math.min(prev.firstTime, point.firstTime);
        prev.lastTime = Math.max(prev.lastTime, point.lastTime);
        prev.min = Math.min(prev.min!, point.min!);
        prev.max = Math.max(prev.max!, point.max!);
        prev.n += point.n;
        prev.avg! -= (prev.avg! / prev.n);
        prev.avg! += (point.avg! / prev.n);
        return;
      }
    }

    // Can't merge: new point
    if (this.pointer < this.bufferSize) {
      this.buffer[this.pointer] = point;
      if (this.pointer >= this.bufferWatermark && this.watermarkObserver && !this.alreadyWarned) {
        this.watermarkObserver();
        this.alreadyWarned = true;
      }
      this.pointer = this.pointer + 1;
    }

    // Unset, so we can merge going forward
    this.forceNextPoint = false;
  }

  snapshot() {
    return this.buffer.filter(s => s !== undefined);
  }

  reset() {
    this.buffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
    this.forceNextPoint = false;
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

  private archiveData = new Map<string, PlotPoint[]>();
  private realtimeBuffers = new Map<string, RealtimeBuffer>();

  constructor(private watermarkObserver: WatermarkObserver) {
  }

  setArchiveData(archiveData: NamedSeries[]) {
    this.archiveData.clear();
    for (const series of archiveData) {
      this.archiveData.set(series.name, series.series);
    }
  }

  addRealtimeValue(seriesName: string, point: PlotPoint) {
    let realtimeBuffer = this.realtimeBuffers.get(seriesName);
    if (!realtimeBuffer) {
      realtimeBuffer = new RealtimeBuffer(this.watermarkObserver);
      this.realtimeBuffers.set(seriesName, realtimeBuffer);
    }

    realtimeBuffer.push(point);
  }

  forceNextPoint() {
    this.realtimeBuffers.forEach(b => b.forceNextPoint = true);
  }

  reset() {
    this.archiveData.clear();
    this.realtimeBuffers.forEach(b => b.reset());
  }

  snapshot(start: number, stop: number): PlotSeries[] {
    const allSeries: PlotSeries[] = [];

    for (const [name, archivePoints] of this.archiveData) {
      const realtimeBuffer = this.realtimeBuffers.get(name)?.snapshot() || [];
      const realtimePoints = realtimeBuffer.filter(s => s !== undefined);

      // Archive sample data contains [null] points for future data (because of empty buckets)
      // Filter these out so that they don't overlap with incoming realtime.
      let archiveCutOff: number | null = null;
      if (realtimePoints.length > 0) {
        archiveCutOff = realtimePoints[0].firstTime;
      }

      let splicedPoints = archivePoints
        .filter(s => archiveCutOff === null || s.firstTime < archiveCutOff);

      // Ignore realtime points if they fall outside of the visible
      // viewport (this avoids seeing a straight line between the
      // archive tail and realtime head when panning).
      if (realtimePoints.length > 0) {
        if (realtimePoints[0].firstTime <= stop) {
          splicedPoints = splicedPoints.concat(realtimePoints);
        }
      }

      splicedPoints = splicedPoints.sort((s1, s2) => s1.firstTime - s2.firstTime);
      allSeries.push(splicedPoints);
    }
    return allSeries;
  }
}
