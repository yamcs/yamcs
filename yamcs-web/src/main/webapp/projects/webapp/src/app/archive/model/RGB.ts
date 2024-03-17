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
      return `rgba(${this.r},${this.g}, ${this.b}, ${opacity})`;
    }
  }
}
