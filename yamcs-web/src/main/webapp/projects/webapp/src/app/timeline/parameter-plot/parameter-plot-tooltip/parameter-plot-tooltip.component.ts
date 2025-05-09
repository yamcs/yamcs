import { CommonModule } from '@angular/common';
import { Component, ElementRef, signal, ViewChild } from '@angular/core';
import { LinePlotPoint } from '@fqqb/timeline';
import { DateTimePipe } from '@yamcs/webapp-sdk';

interface Legend {
  color: string;
  label: string;
  value: any;
}

@Component({
  selector: 'app-parameter-plot-tooltip',
  templateUrl: './parameter-plot-tooltip.component.html',
  styleUrl: './parameter-plot-tooltip.component.css',
  imports: [CommonModule, DateTimePipe],
})
export class ParameterPlotTooltipComponent {
  @ViewChild('tt', { static: true })
  tt: ElementRef<HTMLDivElement>;

  date = signal<Date | null>(null);
  legend = signal<Legend[]>([]);

  show(
    left: number,
    top: number,
    date: Date,
    traces: { [key: string]: any }[],
    points: Array<LinePlotPoint | null>,
    labelFormatter: (value: number) => string,
  ) {
    this.date.set(date);

    const visibleTraces = traces.filter((trace) => trace.visible);

    const legend: Legend[] = [];
    for (let i = 0; i < visibleTraces.length; i++) {
      let value: string | null = null;
      const point = points[i];
      if (point && point.value !== null) {
        value = labelFormatter(point.value);
      }
      legend.push({
        color: visibleTraces[i].lineColor,
        label: visibleTraces[i].parameter,
        value: value,
      });
    }
    this.legend.set(legend);

    const el = this.tt.nativeElement;
    el.style.left = left + 'px';
    el.style.top = top + 'px';
    el.style.display = 'block';
  }

  hide() {
    const el = this.tt.nativeElement;
    el.style.display = 'none';
  }
}
