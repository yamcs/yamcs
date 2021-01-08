import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { BehaviorSubject } from 'rxjs';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { TransferItem } from './TransferItem';

@Component({
  selector: 'app-file-transfer-table',
  templateUrl: './FileTransferTable.html',
  styleUrls: ['./FileTransferTable.css'],
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
      this.displayedColumns$.next([...this.defaultColumns, 'actions']);
    } else {
      this.displayedColumns$.next(this.defaultColumns);
    }
  }

  pauseTransfer(item: TransferItem) {
    const id = item.transfer.id;
    this.yamcs.yamcsClient.pauseCfdpTransfer(this.yamcs.instance!, this.serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }

  resumeTransfer(item: TransferItem) {
    const id = item.transfer.id;
    this.yamcs.yamcsClient.resumeCfdpTransfer(this.yamcs.instance!, this.serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }

  cancelTransfer(item: TransferItem) {
    const id = item.transfer.id;
    this.yamcs.yamcsClient.cancelCfdpTransfer(this.yamcs.instance!, this.serviceName, id).catch(err => {
      this.messageService.showError(err);
    });
  }
}
