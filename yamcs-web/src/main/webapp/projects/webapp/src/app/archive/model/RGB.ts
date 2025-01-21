export class RGB {

  constructor(
    readonly r: number,
    readonly g: number,
    readonly b: number,
  ) { }

  toCssString(opacity?: number) {
    if (opacity === undefined) {
      return `rgb(${this.r},${this.g},${this.b})`;
    } else {
      // Make a non-transparent equivalent color
      const r = 255 - (opacity * (255 - this.r));
      const g = 255 - (opacity * (255 - this.g));
      const b = 255 - (opacity * (255 - this.b));
      return `rgb(${r},${g},${b})`;
    }
  }
}
