import { CB_SET3 } from './palette';

export const PALETTE = CB_SET3;
export const OTHER_COLOR = '#000000';

export class ColorMap {

  // Maps preserve insertion order.
  //
  // We use this attribute to store entries
  // from LRU to MRU.
  private cache = new Map<any, string>();

  // Index into palette. Prefer this over randomization,
  // so that a browser refresh doesn't mess up colorings.
  private nextIndex = 0;

  colorForValue(value: any): string {
    if (value === '__OTHER') {
      return OTHER_COLOR;
    }
    let color = this.cache.get(value);
    if (color) {
      // Remove earlier access record
      this.cache.delete(value);
    } else {
      color = PALETTE[this.nextIndex];
      this.nextIndex++;
      if (this.nextIndex > PALETTE.length - 1) {
        this.nextIndex = 0;
        this.keepSizeWithinLimits();
      }
    }

    // Insert MRU
    this.cache.set(value, color);
    return color;
  }

  private keepSizeWithinLimits() {
    while (this.cache.size > 1000) {
      let i = 0;
      for (var k of this.cache.keys()) {
        if (i++ > 100) {
          break;
        }
        this.cache.delete(k);
      }
    }
  }
}
