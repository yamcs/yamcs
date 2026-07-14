import {
  ChangeDetectionStrategy,
  Component,
  effect,
  input,
} from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { ParameterValue, WebappSdkModule } from '@yamcs/webapp-sdk';
import { LiveExpressionComponent } from '../../../shared/live-expression/live-expression.component';
import { EntryLabel } from '../entry-label/entry-label.component';
import { StackedVerifyEntry } from '../stack-file/StackedEntry';

interface Record {
  parameter: string;
  operator: string;
  value: string;
  expression: string;
  pval?: ParameterValue;
}

@Component({
  selector: 'app-stacked-verify-entry',
  templateUrl: './stacked-verify-entry.component.html',
  styleUrl: './stacked-verify-entry.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [EntryLabel, LiveExpressionComponent, WebappSdkModule],
})
export class StackedVerifyEntryComponent {
  entry = input.required<StackedVerifyEntry>();
  pvals = input.required<{ [key: string]: ParameterValue }>();

  dataSource = new MatTableDataSource<Record>();
  displayedColumns = ['expression', 'evaluation', 'value'];

  tableTrackerFn = (index: number, item: Record) => index;

  constructor() {
    effect(() => {
      const pvals = this.pvals();
      const records: Record[] = [];
      for (const comparison of this.entry().condition) {
        let expression = `'${comparison.parameter}'`;
        switch (comparison.operator) {
          case 'eq':
            expression += ' == ';
            break;
          case 'neq':
            expression += ' != ';
            break;
          case 'lt':
            expression += ' < ';
            break;
          case 'lte':
            expression += ' <= ';
            break;
          case 'gt':
            expression += ' > ';
            break;
          case 'gte':
            expression += ' >= ';
            break;
        }

        if (typeof comparison.value === 'string') {
          if (comparison.value === 'true') {
            expression += 'true';
          } else if (comparison.value === 'false') {
            expression += 'false';
          } else if (!isNaN(comparison.value as any)) {
            expression += comparison.value;
          } else {
            expression += `"${comparison.value}"`;
          }
        } else {
          expression += `${comparison.value}`;
        }

        const record: Record = {
          parameter: comparison.parameter,
          operator: comparison.operator,
          value: comparison.value,
          expression,
          pval: pvals[comparison.parameter],
        };
        records.push(record);
      }

      this.dataSource.data = records;
    });
  }
}
