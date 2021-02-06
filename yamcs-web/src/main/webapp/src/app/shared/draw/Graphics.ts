import { HitCanvas, HitRegionSpecification } from './HitCanvas';

interface RectColorFill {
  x: number;
  y: number;
  width: number;
  height: number;
  color: string;
  opacity?: number;
}

interface RectGradientFill {
  x: number;
  y: number;
  width: number;
  height: number;
  gradient: CanvasGradient;
  opacity?: number;
}

export type RectFill = RectColorFill | RectGradientFill;

export interface TextFill {
  x: number;
  y: number;
  baseline: 'top' | 'middle' | 'bottom';
  align: 'left' | 'right' | 'center';
  font: string;
  color: string;
  text: string;
}

export class Graphics {
  readonly ctx: CanvasRenderingContext2D;

  readonly hitCanvas: HitCanvas;
  readonly hitCtx: CanvasRenderingContext2D;

  constructor(readonly canvas: HTMLCanvasElement) {
    this.ctx = canvas.getContext('2d')!;
    this.hitCanvas = new HitCanvas();
    this.hitCtx = this.hitCanvas.ctx;
  }

  clearHitCanvas() {
    this.hitCanvas.clear();
  }

  resize(width: number, height: number) {
    // Careful not to reset dimensions all the time (it does lots of stuff)
    const intWidth = Math.floor(width);
    const intHeight = Math.floor(height);
    if (this.ctx.canvas.width != intWidth || this.ctx.canvas.height != intHeight) {
      this.ctx.canvas.width = intWidth;
      this.ctx.canvas.height = intHeight;
      this.hitCanvas.ctx.canvas.width = intWidth;
      this.hitCanvas.ctx.canvas.height = intHeight;
    }
  }


  fillRect(fill: RectFill) {
    if ('color' in fill) {
      this.ctx.fillStyle = fill.color.toString();
    } else {
      this.ctx.fillStyle = fill.gradient;
    }

    if (fill.opacity !== undefined) {
      this.ctx.globalAlpha = fill.opacity;
    }

    this.ctx.fillRect(fill.x, fill.y, fill.width, fill.height);

    if (fill.opacity !== undefined) {
      this.ctx.globalAlpha = 1;
    }
  }

  fillText(fill: TextFill) {
    this.ctx.textBaseline = fill.baseline;
    this.ctx.textAlign = fill.align;
    this.ctx.font = fill.font;
    this.ctx.fillStyle = fill.color;
    this.ctx.fillText(fill.text, fill.x, fill.y);
  }

  addHitRegion(region: HitRegionSpecification) {
    this.hitCanvas.beginHitRegion(region);
    return new HitRegionBuilder(this.hitCanvas.ctx);
  }
}

export class HitRegionBuilder {

  constructor(private ctx: CanvasRenderingContext2D) {
  }

  addRect(x: number, y: number, width: number, height: number) {
    this.ctx.fillRect(x, y, width, height);
    return this;
  }
}
