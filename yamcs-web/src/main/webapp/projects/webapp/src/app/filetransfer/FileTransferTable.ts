import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MessageService, Transfer, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { TransferItem } from './TransferItem';

@Component({
  selector: 'app-file-transfer-table',
  templateUrl: './FileTransferTable.html',
  styleUrls: ['./FileTransferTable.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferTable implements OnChanges {

  @Input()
  extraColumns: string[] = [];

  private defaultColumns = [
    'startTime',
    'localFile',
    'direction',
    'remoteFile',
    'size',
    'status',
  ];

  displayedColumns$ = new BehaviorSubject<string[]>(this.defaultColumns);

  isIncomplete = (index: number, item: TransferItem) => {
    return item.transfer.state !== 'COMPLETED';
  };

  @Input()
  serviceName: string;

  @Input()
  dataSource = new MatTableDataSource<TransferItem>();

  @Input()
  showActions = false;

  constructor(private yamcs: YamcsService, private messageService: MessageService) {
  }

  ngOnChanges() {
    if (this.showActions) {
      this.displayedColumns$.next([...this.defaultColumns, ...this.extraColumns, 'actions']);
    } else {
      this.displayedColumns$.next([...this.defaultColumns, ...this.extraColumns]);
    }
  }

  transferPercent(transfer: Transfer) {
    return transfer.totalSize != 0 ? (transfer.sizeTransferred / transfer.totalSize) : 0;
  }

  pauseTransfer(item: TransferItem) {
    const id = item.transfer.id;
    this.yamcs.yamcsClient.pauseFileTransfer(this.yamcs.instance!, this.serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }

  resumeTransfer(item: TransferItem) {
    const id = item.transfer.id;
    this.yamcs.yamcsClient.resumeFileTransfer(this.yamcs.instance!, this.serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }

  cancelTransfer(item: TransferItem) {
    const id = item.transfer.id;
    this.yamcs.yamcsClient.cancelFileTransfer(this.yamcs.instance!, this.serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }
}
