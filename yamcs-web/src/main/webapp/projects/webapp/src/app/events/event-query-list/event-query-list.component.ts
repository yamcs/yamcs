import { Component } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { MessageService, Query, WebappSdkModule, YamcsService, YaSelectOption } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { EditEventQueryDialogComponent } from '../edit-event-query-dialog/edit-event-query-dialog.component';
import { EventsPageTabsComponent } from '../events-page-tabs/events-page-tabs.component';

@Component({
  standalone: true,
  selector: 'app-event-query-list',
  templateUrl: './event-query-list.component.html',
  imports: [
    InstancePageTemplateComponent,
    InstanceToolbarComponent,
    EventsPageTabsComponent,
    WebappSdkModule,
  ],
})
export class EventQueryListComponent {

  displayedColumns = [
    'name',
    'visibility',
    'actions',
  ];

  dataSource = new MatTableDataSource<Query>();

  sourceOptions: YaSelectOption[] = [];

  constructor(
    readonly yamcs: YamcsService,
    private messageService: MessageService,
    private dialog: MatDialog,
  ) {
    this.refreshTable();

    yamcs.yamcsClient.getEventSources(yamcs.instance!).then(sources => {
      this.sourceOptions = sources.map(source => ({ id: source, label: source }));
    });
  }

  private refreshTable() {
    this.yamcs.yamcsClient.getQueries(this.yamcs.instance!, 'events')
      .then(queries => this.dataSource.data = queries)
      .catch(err => this.messageService.showError(err));

  }

  openEditQueryDialog(query: Query) {
    this.dialog.open(EditEventQueryDialogComponent, {
      width: '800px',
      data: {
        query,
        sourceOptions: this.sourceOptions,
      },
    }).afterClosed().subscribe(res => {
      if (res) {
        this.refreshTable();
        this.messageService.showInfo('Query updated');
      }
    });
  }

  openDeleteQueryDialog(query: Query) {
    if (confirm(`Are you sure you want to delete query ${query.name}`)) {
      this.yamcs.yamcsClient.deleteQuery(this.yamcs.instance!, 'events', query.id)
        .then(() => this.refreshTable())
        .catch(err => this.messageService.showError(err));
    }
  }
}
