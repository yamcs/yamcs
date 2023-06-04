export class ScrollBuffer {

  public dirty = false;

  private buffer: (string | undefined)[];
  private pointer = 0;
  private prevPointer?: number;

  constructor(bufferSize: number) {
    this.buffer = Array(bufferSize).fill(undefined);
  }

  add(line: string) {
    this.buffer[this.pointer] = line;
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

  snapshot(): string[] {
    if (this.prevPointer === undefined) {
      return [];
    } else {
      const left = this.buffer.slice(0, this.prevPointer + 1); // Left of pointer (inclusive)
      const right = this.buffer.slice(this.prevPointer + 1); // Right of pointer (exclusive)
      return right.concat(left).filter(s => s !== undefined) as string[];
    }
  }
}
