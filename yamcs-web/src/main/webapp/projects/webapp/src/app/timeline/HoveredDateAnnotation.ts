import {
  Bounds,
  Drawable,
  Graphics,
  Timeline,
  ViewportMouseMoveEvent,
} from '@fqqb/timeline';
import { Formatter } from '@yamcs/webapp-sdk';

export class HoveredDateAnnotation extends Drawable {
  private time?: number;

  private mouseMoveListener = (evt: ViewportMouseMoveEvent) => {
    if (evt.time !== this.time) {
      this.time = evt.time;
      this.reportMutation();
    }
  };
  private mouseLeaveListener = () => {
    this.time = undefined;
    this.reportMutation();
  };

  constructor(
    timeline: Timeline,
    private formatter: Formatter,
  ) {
    super(timeline);
    timeline.addViewportMouseMoveListener(this.mouseMoveListener);
    timeline.addViewportMouseLeaveListener(this.mouseLeaveListener);
  }

  override drawOverlay(g: Graphics): void {
    if (this.time === undefined) {
      return;
    }

    const x = Math.round(this.timeline.positionTime(this.time));
    const text = this.formatter.formatDateTime(this.time, true);
    const fontSize = 12;
    const font = `${fontSize}px Roboto, sans-serif`;
    const whitespaceHeight = 35;
    const padding = 4;
    const lrMargin = 16; // Same as time-range div

    const fm = g.measureText(text, font);

    const textBounds: Bounds = {
      x: x - fm.width / 2 - padding,
      y: g.height - padding - (whitespaceHeight - fontSize) / 2 - fontSize,
      width: padding + fm.width + padding,
      height: padding + fontSize + padding,
    };

    // Avoid clipping this overlay at the edges
    const chartStartX = this.timeline.positionTime(this.timeline.start);
    const chartStopX = this.timeline.positionTime(this.timeline.stop);
    if (textBounds.x < chartStartX + lrMargin) {
      textBounds.x = chartStartX + lrMargin;
    } else if (textBounds.x + textBounds.width > chartStopX - lrMargin) {
      textBounds.x = chartStopX - lrMargin - textBounds.width;
    }

    g.fillRect({
      ...textBounds,
      fill: 'rgba(97, 97, 97, 0.9)',
      rx: 2,
      ry: 2,
    });
    g.fillText({
      x: textBounds.x + padding,
      y: g.height - whitespaceHeight / 2,
      align: 'left',
      baseline: 'middle',
      color: 'white',
      font,
      text,
    });
  }

  override disconnectedCallback(): void {
    this.timeline.removeViewportMouseMoveListener(this.mouseMoveListener);
    this.timeline.removeViewportMouseLeaveListener(this.mouseLeaveListener);
  }
}
