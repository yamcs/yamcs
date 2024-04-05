import { StreamData } from '@yamcs/webapp-sdk';

export class StreamBuffer {

  public dirty = false;

  private buffer: (StreamData | undefined)[];
  private bufferSize = 500;
  private pointer = 0;
  private prevPointer?: number;

  constructor() {
    this.buffer = Array(this.bufferSize).fill(undefined);
  }

  add(streamData: StreamData) {
    this.buffer[this.pointer] = streamData;
    this.prevPointer = this.pointer;
    this.pointer = (this.pointer + 1) % this.buffer.length;
    this.dirty = true;
  }

  reset() {
    this.buffer.fill(undefined);
    this.pointer = 0;
    this.prevPointer = undefined;
    this.dirty = true;
  }

  snapshot(): StreamData[] {
    if (this.prevPointer === undefined) {
      return [];
    } else {
      const left = this.buffer.slice(0, this.prevPointer + 1); // Left of pointer (inclusive)
      const right = this.buffer.slice(this.prevPointer + 1); // Right of pointer (exclusive)
      return right.concat(left).filter(s => s !== undefined) as StreamData[];
    }
  }
}
