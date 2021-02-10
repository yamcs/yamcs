const WHITE = 'rgb(255,255,255)';

export interface HitRegionSpecification {
  id: string;
  click?: () => void;
  mouseEnter?: () => void;
  mouseMove?: (pressing: boolean) => void;
  mouseOut?: () => void;
  mouseDown?: () => void;
  mouseUp?: () => void;
  cursor?: string;
}

export class HitCanvas {

  readonly ctx: CanvasRenderingContext2D;
  private regions: { [key: string]: HitRegionSpecification; } = {};

  constructor(width?: number, height?: number) {
    const canvas = document.createElement('canvas');
    canvas.width = width || canvas.width;
    canvas.height = height || canvas.height;
    this.ctx = canvas.getContext('2d')!;
  }

  clear() {
    this.regions = {};
    this.ctx.fillStyle = WHITE;
    this.ctx.fillRect(0, 0, this.ctx.canvas.width, this.ctx.canvas.height);
  }

  beginHitRegion(hitRegion: HitRegionSpecification) {
    const color = this.generateUniqueColor();
    this.regions[color] = hitRegion;

    this.ctx.beginPath();
    this.ctx.fillStyle = color;
    return color;
  }

  getActiveRegion(x: number, y: number): HitRegionSpecification | undefined {
    const pixel = this.ctx.getImageData(x, y, 1, 1).data;
    const color = `rgb(${pixel[0]},${pixel[1]},${pixel[2]})`;
    return this.regions[color] || undefined;
  }

  private generateUniqueColor(): string {
    while (true) {
      const r = Math.round(Math.random() * 255);
      const g = Math.round(Math.random() * 255);
      const b = Math.round(Math.random() * 255);
      const color = `rgb(${r},${g},${b})`;

      if (!this.regions[color] && color !== WHITE) {
        return color;
      }
    }
  }
}
