export class Color {

  static BLACK = new Color(0, 0, 0);
  static WHITE = new Color(255, 255, 255);

  constructor(
    public red: number,
    public green: number,
    public blue: number,
  ) {}

  toString() {
    return `rgba(${this.red},${this.green},${this.blue},255)`;
  }
}
