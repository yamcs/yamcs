import { Drawable, Graphics, Path, Timeline } from '@fqqb/timeline';
import { ReplayRequest } from '@yamcs/webapp-sdk';

export class ReplayOverlay extends Drawable {

  private pattern;

  private _replayRequest?: ReplayRequest;

  constructor(timeline: Timeline) {
    super(timeline);

    const offscreen = document.createElement('canvas');
    offscreen.width = 30;
    offscreen.height = 30;
    const ctx = offscreen.getContext('2d')!;

    ctx.fillStyle = 'rgba(148, 0, 211, 0.07)';
    ctx.fillRect(0, 0, 30, 30);

    ctx.beginPath();
    ctx.moveTo(30, 0);
    ctx.lineTo(0, 30);
    ctx.strokeStyle = 'rgba(221, 221, 221, 0.2)';
    ctx.stroke();

    this.pattern = ctx.createPattern(offscreen, 'repeat')!;
  }

  override drawOverlay(g: Graphics) {
    if (!this.replayRequest) {
      return;
    }

    const { start, stop, endAction } = this.replayRequest;

    if (start && stop) {
      const x1 = Math.round(this.timeline.positionTime(new Date(start).getTime()));
      const x2 = Math.round(this.timeline.positionTime(new Date(stop).getTime()));
      g.fillRect({
        x: x1 + 0.5,
        y: 0,
        width: x2 - x1,
        height: g.ctx.canvas.height,
        fill: this.pattern,
      });

      g.strokePath({
        color: 'rgba(148, 0, 211, 0.3)',
        lineWidth: 1,
        path: new Path(x1 + 0.5, 0).lineTo(x1 + 0.5, g.ctx.canvas.height),
      });

      g.strokePath({
        color: 'rgba(148, 0, 211, 0.3)',
        lineWidth: 1,
        path: new Path(x2 + 0.5, 0).lineTo(x2 + 0.5, g.ctx.canvas.height),
      });

      if (endAction === 'LOOP') {
        const { fontFamily, textSize } = this.timeline;
        g.fillText({
          x: x2 - 3,
          y: 0,
          align: 'right',
          baseline: 'top',
          text: '∞',
          color: 'darkviolet',
          font: `${textSize}px ${fontFamily}`,
        });
      }
    }
  }

  get replayRequest() { return this._replayRequest; }
  set replayRequest(replayRequest) {
    this._replayRequest = replayRequest;
    this.reportMutation();
  }
}
