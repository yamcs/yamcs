import { ChangeDetectionStrategy, Component, Input } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { TransferItem } from './TransferItem';

@Component({
  selector: 'app-file-transfer-table',
  templateUrl: './FileTransferTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferTable {

  displayedColumns = [
    'startTime',
    'localFile',
    'direction',
    'remoteFile',
    'size',
    'status',
  ];

  @Input()
  dataSource = new MatTableDataSource<TransferItem>();
}
