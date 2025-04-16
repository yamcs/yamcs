import { CommonModule } from '@angular/common';
import { Component, ElementRef, signal, ViewChild } from '@angular/core';
import { Item } from '@fqqb/timeline';
import { DateTimePipe, Formatter } from '@yamcs/webapp-sdk';
import { LegendEntry } from '../LegendEntry';
import { State } from '../State';
import { StateLegend } from '../StateLegend';

@Component({
  selector: 'app-parameter-states-tooltip',
  templateUrl: './parameter-states-tooltip.component.html',
  styleUrl: './parameter-states-tooltip.component.css',
  imports: [CommonModule, DateTimePipe],
})
export class ParameterStatesTooltipComponent {
  @ViewChild('tt', { static: true })
  tt: ElementRef<HTMLDivElement>;

  start = signal<Date | null>(null);
  stop = signal<Date | null>(null);
  legend = signal<LegendEntry[]>([]);

  constructor(private formatter: Formatter) {}

  show(left: number, top: number, legend: StateLegend, item?: Item) {
    let state: State | undefined = undefined;
    if (item) {
      this.start.set(new Date(item.start));
      this.stop.set(item.stop ? new Date(item.stop) : null);
      state = item.data.range;
    }

    const richEntries: LegendEntry[] = [];

    let mostFrequentEntry: LegendEntry | undefined = undefined;
    for (const [label, color] of legend.entries()) {
      if (label === '__OTHER') {
        const entry: LegendEntry = {
          label,
          color,
          count: state?.otherCount || 0,
          mostFrequent: false,
        };
        richEntries.push(entry);
      } else {
        let count = 0;
        if (state) {
          for (const countedValue of state.values) {
            if (countedValue.value === label) {
              count = countedValue.count;
              break;
            }
          }
        }

        const entry: LegendEntry = { label, color, count, mostFrequent: false };
        richEntries.push(entry);

        if (
          mostFrequentEntry === undefined ||
          count > mostFrequentEntry.count
        ) {
          mostFrequentEntry = entry;
        }
      }
    }
    if (mostFrequentEntry) {
      mostFrequentEntry.mostFrequent = true;
    }
    this.legend.set(richEntries);

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
