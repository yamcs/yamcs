export class Color {

  static BLACK = new Color(0, 0, 0);
  static WHITE = new Color(255, 255, 255);
  static DARK_GRAY = new Color(150, 150, 150);

  static BUTTON = new Color(239, 240, 241);
  static BUTTON_DARKER = new Color(164, 168, 172);
  static BUTTON_DARKEST = Color.BLACK;
  static BUTTON_LIGHTEST = Color.WHITE;

  constructor(
    public red: number,
    public green: number,
    public blue: number,
    public alpha = 255,
  ) { }

  withAlpha(alpha: number) {
    return new Color(this.red, this.green, this.blue, alpha);
  }

  brighter() {
    let r = this.red;
    let g = this.green;
    let b = this.blue;

    const i = Math.floor(1.0 / (1.0 - 0.7));
    if (r === 0 && g === 0 && b === 0) {
        return new Color(i, i, i);
    }
    if (r > 0 && r < i) {
      r = i;
    }
    if (g > 0 && g < i) {
      g = i;
    }
    if (b > 0 && b < i) {
      b = i;
    }

    return new Color(Math.min(Math.floor(this.red / 0.7), 255),
                     Math.min(Math.floor(this.green / 0.7), 255),
                     Math.min(Math.floor(this.blue / 0.7), 255));
  }

  darker() {
    return new Color(
      Math.max(Math.floor(this.red * 0.7), 0),
      Math.max(Math.floor(this.green * 0.7), 0),
      Math.max(Math.floor(this.blue * 0.7), 0),
    );
  }

  toString() {
    return `rgba(${this.red},${this.green},${this.blue},${this.alpha})`;
  }
}
