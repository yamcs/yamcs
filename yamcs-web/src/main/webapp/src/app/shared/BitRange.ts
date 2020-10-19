export class BitRange {

  constructor(readonly start: number, readonly bitlength: number) {
  }

  get stop() {
    return this.start + this.bitlength;
  }

  join(other: BitRange) {
    const start = Math.min(this.start, other.start);
    const stop = Math.max(this.stop, other.stop);
    return new BitRange(start, stop - start);
  }

  joinBit(bitpos: number) {
    const start = Math.min(this.start, bitpos);
    const stop = Math.max(this.stop, bitpos);
    return new BitRange(start, stop - start);
  }

  containsBit(bitpos: number) {
    return this.start <= bitpos && bitpos <= this.stop;
  }

  containsBitExclusive(bitpos: number) {
    return this.start < bitpos && bitpos < this.stop;
  }

  overlaps(other: BitRange) {
    return other.start < this.stop && this.start < other.stop;
  }

  intersect(other: BitRange): BitRange | null {
    if (other.start > this.stop || this.start > other.stop) {
      return null;
    }
    const start = Math.max(this.start, other.start);
    const stop = Math.min(this.stop, other.stop);
    return new BitRange(start, stop - start);
  }

  equals(other: BitRange): boolean {
    return this.start === other.start && this.bitlength === other.bitlength;
  }

  toString() {
    return `${this.start}-${this.stop} (${this.bitlength} bits)`;
  }
}
