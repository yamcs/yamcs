import { Component, Input } from '@angular/core';
import { Record, Table, Value } from '@yamcs/client';

@Component({
  selector: 'app-record',
  templateUrl: './RecordComponent.html',
  styleUrls: ['./RecordComponent.css'],
})
export class RecordComponent {

  @Input()
  table: Table;

  @Input()
  record: Record;

  getColumnValue(record: Record, columnName: string): Value | null {
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
