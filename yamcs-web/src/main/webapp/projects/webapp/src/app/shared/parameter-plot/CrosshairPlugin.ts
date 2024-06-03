/**
 * Renders the crosshair on the axis only.
 * The in-plot crosshair is drawn via a drawHighlightPointCallback
 * in ParameterPlot because the underlaycallback is drawn
 * on top of this plugin.
 */
export default class CrosshairPlugin {

  private canvas: any;

  constructor() {
    this.canvas = document.createElement('canvas');
  }

  activate(g: any) {
    g.graphDiv.appendChild(this.canvas);
    return {
      select: this.select,
      deselect: this.deselect,
    };
  }

  select(e: any) {
    const width = e.dygraph.width_;
    const height = e.dygraph.height_;
    this.canvas.width = width;
    this.canvas.height = height;
    this.canvas.style.width = width + 'px'; // for IE
    this.canvas.style.height = height + 'px'; // for IE

    const ctx = this.canvas.getContext('2d');
    ctx.clearRect(0, 0, width, height);
    ctx.strokeStyle = '#e1e1e1';

    // Horizontal guides
    ctx.beginPath();
    for (const point of e.dygraph.selPoints_) {
      const canvasy = Math.floor(point.canvasy) + 0.5; // crisper rendering
      ctx.moveTo(0, canvasy);
      ctx.lineTo(width, canvasy);
    }
    ctx.stroke();
    ctx.closePath();
  }

  deselect(e: any) {
    const ctx = this.canvas.getContext('2d');
    ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
  }

  destroy() {
    this.canvas = null;
  }

  toString() {
    return 'Crosshair Plugin';
  }
}
