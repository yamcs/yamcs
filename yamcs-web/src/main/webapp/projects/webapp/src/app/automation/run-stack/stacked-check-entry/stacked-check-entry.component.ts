import { Component, effect, input } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { ParameterValue, WebappSdkModule } from '@yamcs/webapp-sdk';
import { EntryLabel } from '../entry-label/entry-label.component';
import { StackedCheckEntry } from '../stack-file/StackedEntry';

interface Record {
  parameter: string;
  pval?: ParameterValue;
}

@Component({
  selector: 'app-stacked-check-entry',
  templateUrl: './stacked-check-entry.component.html',
  styleUrl: './stacked-check-entry.component.css',
  imports: [EntryLabel, WebappSdkModule],
})
export class StackedCheckEntryComponent {
  entry = input.required<StackedCheckEntry>();
  pvals = input.required<{ [key: string]: ParameterValue }>();

  dataSource = new MatTableDataSource<Record>();
  displayedColumns = ['parameter', 'level', 'value'];

  constructor() {
    effect(() => {
      const pvals = this.pvals();
      const records: Record[] = [];
      for (const check of this.entry().parameters) {
        const record: Record = {
          parameter: check.parameter,
          pval: pvals[check.parameter],
        };
        records.push(record);
      }

      this.dataSource.data = records;
    });
  }
}
