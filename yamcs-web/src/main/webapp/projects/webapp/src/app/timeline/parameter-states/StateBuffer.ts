import { Formatter, ParameterValue, utils } from '@yamcs/webapp-sdk';
import { CountedValue } from './CountedValue';
import { State } from './State';
import { StateRemapper } from './StateRemapper';

export type WatermarkObserver = () => void;

class RealtimeState implements State {
  start: number;
  stop: number;
  values: CountedValue[];
  mostFrequentValue: CountedValue;
  otherCount = 0;
  mixed = false;
  other = false;
}

class RealtimeBuffer {
  private buffer: (RealtimeState | undefined)[];
  private bufferSize = 500;
  private bufferWatermark = 400;
  private pointer = 0;
  private alreadyWarned = false;

  constructor(
    private maxGap: number,
    private formatter: Formatter,
    private watermarkObserver: WatermarkObserver,
  ) {
    this.buffer = Array(this.bufferSize).fill(undefined);
  }

  push(pval: ParameterValue) {
    let prev: State | undefined = undefined;
    if (this.pointer > 0) {
      prev = this.buffer[this.pointer - 1];
    }

    const gentime = utils.toDate(pval.generationTime).getTime();
    const value = this.formatter.formatValue(pval.engValue);
    const countedValue: CountedValue = { value, count: 1 };

    const state = new RealtimeState();
    state.start = gentime;
    state.stop = state.start;
    state.values = [countedValue];
    state.mostFrequentValue = countedValue;

    // Merge with previous state when possible
    if (prev && prev.values[0].value === value) {
      prev.stop = state.start;
      prev.values[0].count++;
      return;
    } else if (prev && (prev.stop - state.start) <= this.maxGap) {
      // Extend prev
      prev.stop = state.start;
    }

    // Can't merge: new state
    if (this.pointer < this.bufferSize) {
      this.buffer[this.pointer] = state;
      if (this.pointer >= this.bufferWatermark && this.watermarkObserver && !this.alreadyWarned) {
        this.watermarkObserver();
        this.alreadyWarned = true;
      }
      this.pointer = this.pointer + 1;
    }
  }

  snapshot() {
    return this.buffer.filter(s => s !== undefined);
  }

  reset() {
    this.buffer.fill(undefined);
    this.pointer = 0;
    this.alreadyWarned = false;
  }
}

export class StateBuffer {

  private archiveData: State[] = [];
  private realtimeBuffer: RealtimeBuffer;

  constructor(
    private maxGap: number,
    formatter: Formatter,
    private stateRemapper: StateRemapper,
    watermarkObserver: WatermarkObserver,
  ) {
    this.realtimeBuffer = new RealtimeBuffer(maxGap, formatter, watermarkObserver);
  }

  setArchiveData(archiveData: State[]) {
    this.archiveData = archiveData;
  }

  addRealtimeValue(pval: ParameterValue) {
    this.realtimeBuffer.push(pval);
  }

  reset() {
    this.archiveData.length = 0;
    this.realtimeBuffer.reset();
  }

  snapshot(start: number, stop: number): State[] {
    const archiveStates = this.archiveData;
    const realtimeStates = this.realtimeBuffer.snapshot();

    // Connect the archive tail with the realtime head.
    // Does not attempt to merge (probably not worth it).
    if (archiveStates.length > 0 && realtimeStates.length > 0) {
      const archiveTail = archiveStates[archiveStates.length - 1];
      const realtimeHead = realtimeStates[0];
      if ((realtimeHead.start - archiveTail.stop) <= this.maxGap) {
        archiveTail.stop = realtimeHead.start;
      }
    }

    // Filter out everything outside of visible window, so that
    // it doesn't impact legend calculation.
    let allStates: State[] = [...archiveStates, ...realtimeStates]
      .filter(state => state.stop >= start && state.start <= stop);

    allStates = this.stateRemapper.applyMappings(allStates);
    return allStates;
  }
}
