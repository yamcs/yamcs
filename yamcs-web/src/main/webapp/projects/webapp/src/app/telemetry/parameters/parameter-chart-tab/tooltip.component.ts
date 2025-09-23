import { CommonModule } from '@angular/common';
import { Component, ElementRef, signal, ViewChild } from '@angular/core';
import { LinePoint } from '@fqqb/timeline';
import { DateTimePipe } from '@yamcs/webapp-sdk';
import { Legend } from './Legend';

interface TooltipLegend {
  traceId: string;
  color: string;
  label: string;
  value: any;
}

@Component({
  selector: 'app-parameter-chart-tooltip',
  templateUrl: './tooltip.component.html',
  styleUrl: './tooltip.component.css',
  imports: [CommonModule, DateTimePipe],
})
export class ParameterChartTooltipComponent {
  @ViewChild('tt', { static: true })
  tt: ElementRef<HTMLDivElement>;

  date = signal<Date | null>(null);
  tooltipLegend = signal<TooltipLegend[]>([]);

  show(
    left: number,
    top: number,
    date: Date,
    legend: Legend,
    trace2point: Map<string, LinePoint>,
    labelFormatter: (value: number) => string,
  ) {
    this.date.set(date);

    const tooltipLegend: TooltipLegend[] = [];

    const legendItems = legend.getItems();
    for (const legendItem of legendItems) {
      let value: string | null = null;
      const point = trace2point.get(legendItem.traceId);
      if (point && point.y !== null) {
        value = labelFormatter(point.y);
      }
      tooltipLegend.push({
        traceId: legendItem.traceId,
        color: legendItem.color,
        label: legendItem.label,
        value,
      });
    }
    this.tooltipLegend.set(tooltipLegend);

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
