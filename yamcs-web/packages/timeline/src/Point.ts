/**
 * XY pair in a cartesian coordinate system
 */
export default class Point {
  constructor(readonly x: number, readonly y: number) { }

  plus(point: Point) {
    return new Point(this.x + point.x, this.y + point.y);
  }

  minus(point: Point) {
    return new Point(this.x - point.x, this.y - point.y);
  }

  withX(x: number) {
    return new Point(x, this.y);
  }

  withY(y: number) {
    return new Point(this.x, y);
  }

  distanceTo(point: Point) {
    return Math.sqrt((point.x - this.x) * (point.x - this.x) + (point.y - this.y) * (point.y - this.y));
  }

  /**
   * Returns a new point horizontally translated by the given shift value
   * @param x horizontal shift
   */
  tx(x: number) {
    return new Point(this.x + x, this.y);
  }

  /**
   * Returns a new point vertically translated by the given shift value
   * @param y vertical shift
   */
  ty(y: number) {
    return new Point(this.x, this.y + y);
  }
}
