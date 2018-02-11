import { Component, Input } from '@angular/core';
import { Record, Table } from '../../../yamcs-client';

@Component({
  selector: 'app-record',
  templateUrl: './record.component.html',
  styleUrls: ['./record.component.css'],
})
export class RecordComponent {

  @Input()
  table: Table;

  @Input()
  record: Record;

  getColumnValue(record: Record, columnName: string) {
    for (const column of record.column) {
      if (column.name === columnName) {
        return column.value;
      }
    }
    return null;
  }

  getBinaryColumnValue(record: Record, columnName: string) {
    for (const column of record.column) {
      if (column.name === columnName) {
        return column.value.binaryValue;
      }
    }
    return null;
  }
}
