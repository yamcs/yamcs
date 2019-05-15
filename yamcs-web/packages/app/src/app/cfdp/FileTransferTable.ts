import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Transfer } from '@yamcs/client';

@Component({
  selector: 'app-file-transfer-table',
  templateUrl: './FileTransferTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferTable {

  displayedColumns = [
    'localFile',
    'direction',
    'remoteFile',
    'size',
    'progress',
    'status',
  ];

  @Input()
  dataSource = new MatTableDataSource<Transfer>();
}
