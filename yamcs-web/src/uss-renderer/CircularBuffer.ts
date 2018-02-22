import { SampleBuffer, Sample } from './SampleBuffer';

/**
 * Sample buffer that limits samples to a fixed total.
 * When the buffer is full, samples are dropped in FIFO order.
 */
export class CircularBuffer implements SampleBuffer {

  private buf: Sample[];
  private pointer = 0;

  constructor(bufferSize: number) {
    this.buf = Array(bufferSize).fill(undefined);
  }

  push(sample: Sample) {
    this.buf[this.pointer] = sample;
    this.pointer = (this.pointer + 1) % this.buf.length;
  }

  /**
   * Returns a copy of this buffer's current content with samples
   * sorted chronologically. This is a relatively expensive operation.
   */
  snapshot() {
    return this.buf
      .filter(s => s !== undefined)
      .sort((s1, s2) => s1[0].getTime() - s2[0].getTime());
  }
}
