export const OTHER_COLOR = '#000000';

// Adapted from cb-Set3
// https://google.github.io/palette.js/
//
// - Removed gray (#d9d9d9)
// - Moved palid yellow (#ffffb3) to the end
export const PALETTE = [
  '#8dd3c7', '#bebada', '#fb8072', '#80b1d3', '#fdb462',
  '#b3de69', '#fccde5', '#bc80bd', '#ccebc5', '#ffffb3',
];

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

  reset() {
    this.cache.clear();
    this.nextIndex = 0;
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
