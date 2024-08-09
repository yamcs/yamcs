import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { MatTableDataSource } from '@angular/material/table';
import { MessageService, Transfer, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { TransferItem } from '../shared/TransferItem';

@Component({
  standalone: true,
  selector: 'app-file-transfer-table',
  templateUrl: './file-transfer-table.component.html',
  styleUrl: './file-transfer-table.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class FileTransferTableComponent implements OnChanges {

  @Input()
  extraColumns: string[] = [];

  private defaultColumns = [
    'startTime',
    'localEntity',
    'localFile',
    'direction',
    'remoteEntity',
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
    this.displayedColumns$.next([...this.defaultColumns, ...this.extraColumns, 'actions']);
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
