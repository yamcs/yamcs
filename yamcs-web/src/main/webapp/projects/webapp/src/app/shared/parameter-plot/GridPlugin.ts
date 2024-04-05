import { AlarmZone } from './dygraphs';

/**
 * Draws the gridlines, i.e. the gray horizontal & vertical lines running the
 * length of the chart.
 *
 * This source file is modified from the official Dygraphs GridPlugin:
 * https://github.com/danvk/dygraphs/blob/master/src/plugins/grid.js
 *
 * The customization consists of adding awareness of alarm limits such that gridlines
 * (drawn in underlay callback) are not drawn on top of them. (due to the opacity this
 * leads to a weird color effect on the limit line).
 */
export default class GridPlugin {

  private yLimits: number[] = [];

  activate(g: any) {
    return {
      willDrawChart: this.willDrawChart
    };
  }

  setAlarmZones(alarmZones: AlarmZone[]) {
    this.yLimits = [];
    for (const zone of alarmZones) {
      this.yLimits.push(zone.y1IsLimit ? zone.y1 : zone.y2);
    }
  }

  willDrawChart(e: any) {
    // Draw the new X/Y grid. Lines appear crisper when pixels are rounded to
    // half-integers. This prevents them from drawing in two rows/cols.
    const g = e.dygraph;
    const ctx = e.drawingContext;
    const layout = g.layout_;
    const area = e.dygraph.plotter_.area;

    if (g.getOptionForAxis('drawGrid', 'y')) {
      const axes = ['y', 'y2'];
      const strokeStyles: any[] = [];
      const lineWidths: any[] = [];
      const drawGrid: any[] = [];
      const stroking: any[] = [];
      const strokePattern: any[] = [];
      for (let i = 0; i < axes.length; i++) {
        drawGrid[i] = g.getOptionForAxis('drawGrid', axes[i]);
        if (drawGrid[i]) {
          strokeStyles[i] = g.getOptionForAxis('gridLineColor', axes[i]);
          lineWidths[i] = g.getOptionForAxis('gridLineWidth', axes[i]);
          strokePattern[i] = g.getOptionForAxis('gridLinePattern', axes[i]);
          stroking[i] = strokePattern[i] && (strokePattern[i].length >= 2);
        }
      }
      const ticks = layout.yticks;
      ctx.save();
      // draw grids for the different y axes
      for (const tick of ticks) {
        if (!tick.has_tick) {
          continue;
        }
        if (this.yLimits.indexOf(parseFloat(tick.label)) >= 0) {
          continue;
        }
        const axis = tick.axis;
        if (drawGrid[axis]) {
          ctx.save();
          if (stroking[axis]) {
            if (ctx.setLineDash) {
              ctx.setLineDash(strokePattern[axis]);
            }
          }
          ctx.strokeStyle = strokeStyles[axis];
          ctx.lineWidth = lineWidths[axis];

          const x = this.halfUp(area.x);
          const y = this.halfDown(area.y + tick.pos * area.h);
          ctx.beginPath();
          ctx.moveTo(x, y);
          ctx.lineTo(x + area.w, y);
          ctx.stroke();

          ctx.restore();
        }
      }
      ctx.restore();
    }

    // draw grid for x axis
    if (g.getOptionForAxis('drawGrid', 'x')) {
      const ticks = layout.xticks;
      ctx.save();
      const strokePattern = g.getOptionForAxis('gridLinePattern', 'x');
      const stroking = strokePattern && (strokePattern.length >= 2);
      if (stroking) {
        if (ctx.setLineDash) {
          ctx.setLineDash(strokePattern);
        }
      }
      ctx.strokeStyle = g.getOptionForAxis('gridLineColor', 'x');
      ctx.lineWidth = g.getOptionForAxis('gridLineWidth', 'x');
      for (const tick of ticks) {
        if (!tick.has_tick) {
          continue;
        }
        const x = this.halfUp(area.x + tick.pos * area.w);
        const y = this.halfDown(area.y + area.h);
        ctx.beginPath();
        ctx.moveTo(x, y);
        ctx.lineTo(x, area.y);
        ctx.closePath();
        ctx.stroke();
      }
      if (stroking) {
        if (ctx.setLineDash) {
          ctx.setLineDash([]);
        }
      }
      ctx.restore();
    }
  }

  private halfUp(x: number) {
    return Math.round(x) + 0.5;
  }

  private halfDown(y: number) {
    return Math.round(y) - 0.5;
  }

  toString() {
    return 'Gridline Plugin';
  }

  destroy() {
  }
}
