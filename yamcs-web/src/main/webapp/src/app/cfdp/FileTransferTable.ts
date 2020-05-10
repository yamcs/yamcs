import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { TransferItem } from './TransferItem';

@Component({
  selector: 'app-file-transfer-table',
  templateUrl: './FileTransferTable.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FileTransferTable implements OnChanges {

  private defaultColumns = [
    'startTime',
    'localFile',
    'direction',
    'remoteFile',
    'size',
    'status',
  ];

  displayedColumns$ = new BehaviorSubject<String[]>(this.defaultColumns);

  @Input()
  dataSource = new MatTableDataSource<TransferItem>();

  @Input()
  showActions = false;

  constructor(private yamcs: YamcsService, private messageService: MessageService) {
  }

  ngOnChanges() {
    if (this.showActions) {
      this.displayedColumns$.next([...this.defaultColumns, 'actions']);
    } else {
      this.displayedColumns$.next(this.defaultColumns);
    }
  }

  pauseTransfer(transfer: TransferItem) {
    this.yamcs.yamcsClient.pauseCfdpTransfer(this.yamcs.instance!, transfer.id).catch(err => {
      this.messageService.showError(err);
    });
  }

  resumeTransfer(transfer: TransferItem) {
    this.yamcs.yamcsClient.resumeCfdpTransfer(this.yamcs.instance!, transfer.id).catch(err => {
      this.messageService.showError(err);
    });
  }

  cancelTransfer(transfer: TransferItem) {
    this.yamcs.yamcsClient.cancelCfdpTransfer(this.yamcs.instance!, transfer.id).catch(err => {
      this.messageService.showError(err);
    });
  }
}
