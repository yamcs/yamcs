
export class Font {

  constructor(
    public name: string,
    public height: number,
    public style: number,
    public pixels: boolean,
  ) {}

  getStyle() {
    const textStyle: {[key: string]: any} = {
      'font-family': this.name,
      'font-size': this.height,
    };
    if (this.style === 1) {
      textStyle['font-weight'] = 'bold';
    } else if (this.style !== 0) {
      console.warn(`Unsupported font style ${this.style}`);
    }

    return textStyle;
  }
}
