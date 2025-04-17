import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import {
  MessageService,
  Query,
  WebappSdkModule,
  YamcsService,
  YaSelectOption,
} from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { EditPacketQueryDialogComponent } from '../edit-packet-query-dialog/edit-packet-query-dialog.component';
import { PacketsPageTabsComponent } from '../packets-page-tabs/packets-page-tabs.component';

@Component({
  selector: 'app-packet-query-list',
  templateUrl: './packet-query-list.component.html',
  imports: [PacketsPageTabsComponent, WebappSdkModule],
})
export class PacketQueryListComponent {
  displayedColumns = ['name', 'visibility', 'actions'];

  dataSource = new MatTableDataSource<Query>();

  nameOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'ANY', label: 'Any name' },
  ]);

  linkOptions$ = new BehaviorSubject<YaSelectOption[]>([
    { id: 'ANY', label: 'Any link' },
  ]);

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private dialog: MatDialog,
  ) {
    this.refreshTable();

    this.yamcs.yamcsClient
      .getPacketNames(this.yamcs.instance!)
      .then((message) => {
        for (const name of message.packets || []) {
          this.nameOptions$.next([
            ...this.nameOptions$.value,
            {
              id: name,
              label: name,
            },
          ]);
        }
        for (const name of message.links || []) {
          this.linkOptions$.next([
            ...this.linkOptions$.value,
            {
              id: name,
              label: name,
            },
          ]);
        }
      });
  }

  private refreshTable() {
    this.yamcs.yamcsClient
      .getQueries(this.yamcs.instance!, 'packets')
      .then((queries) => (this.dataSource.data = queries))
      .catch((err) => this.messageService.showError(err));
  }

  openEditQueryDialog(query: Query) {
    this.dialog
      .open(EditPacketQueryDialogComponent, {
        width: '800px',
        data: {
          query,
          nameOptions: this.nameOptions$.value,
          linkOptions: this.linkOptions$.value,
        },
      })
      .afterClosed()
      .subscribe((res) => {
        if (res) {
          this.refreshTable();
          this.messageService.showInfo('Query updated');
        }
      });
  }

  openDeleteQueryDialog(query: Query) {
    if (confirm(`Are you sure you want to delete query ${query.name}`)) {
      this.yamcs.yamcsClient
        .deleteQuery(this.yamcs.instance!, 'packets', query.id)
        .then(() => this.refreshTable())
        .catch((err) => this.messageService.showError(err));
    }
  }
}
