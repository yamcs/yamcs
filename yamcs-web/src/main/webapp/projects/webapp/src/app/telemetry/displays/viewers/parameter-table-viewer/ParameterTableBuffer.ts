import { NamedObjectId, ParameterValue } from '@yamcs/webapp-sdk';

/**
 * Sample buffer that limits samples to a fixed total.
 * When the buffer is full, samples are dropped in FIFO order.
 */
export class ParameterTableBuffer {

  private buf: { [key: string]: ParameterValue; }[];
  private pointer = 0;

  private latestValues = new Map<string, ParameterValue>();

  constructor(bufferSize = 10) {
    this.buf = Array(bufferSize).fill(undefined);
  }

  push(delivery: ParameterValue[], idMapping: { [key: number]: NamedObjectId; }) {
    const byName: { [key: string]: ParameterValue; } = {};
    for (const pval of delivery) {
      const id = idMapping[pval.numericId];
      byName[id.name] = pval;
    }
    this.buf[this.pointer] = byName;
    this.pointer = (this.pointer + 1) % this.buf.length;

    for (const pval of delivery) {
      const id = idMapping[pval.numericId];
      this.latestValues.set(id.name, pval);
    }
  }

  getLatestValue(qualifiedName: string) {
    return this.latestValues.get(qualifiedName);
  }

  setSize(bufferSize: number) {
    this.buf = Array(bufferSize).fill(undefined);
    this.pointer = 0;
  }

  /**
   * Returns a copy of this buffer's current content. Does not sort as it should show actual order.
   */
  snapshot() {
    if (this.pointer === 0) {
      return [...this.buf];
    } else {
      return [
        ...this.buf.slice(this.pointer),
        ...this.buf.slice(0, this.pointer),
      ].reverse();
    }
  }
}
