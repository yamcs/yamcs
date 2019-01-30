export class Box {

  constructor(
    public x: number,
    public y: number,
    public width: number,
    public height: number,
  ) {}

  getOutline(strokeWidth: number) {
    const inset = Math.max(1, strokeWidth) / 2.0;
    const inset1 = Math.floor(inset);
    const inset2 = Math.ceil(inset);

    const x = this.x + inset1;
    const y = this.y + inset1;
    const width = this.width - inset1 - inset2;
    const height = this.width - inset1 - inset2;
    return new Box(x, y, width, height);
  }
}
