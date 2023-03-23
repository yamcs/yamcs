import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { AuditRecord } from '../../client';

export interface RowGroup {
  grouper: string;
  dataSource: MatLegacyTableDataSource<Row>;
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
