import { MatTableDataSource } from '@angular/material/table';
import { AuditRecord } from '../../client';

export interface RowGroup {
  grouper: string;
  dataSource: MatTableDataSource<Row>;
}

export interface RequestOption {
  key: string;
  value: string;
}

export interface Row {
  expanded: boolean;
  item: AuditRecord;
  requestOptions: Request[];
}
