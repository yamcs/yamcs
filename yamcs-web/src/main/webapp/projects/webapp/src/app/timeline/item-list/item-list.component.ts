import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { MessageService, TimelineItem, TrackBySelectionModel, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';
import { CreateItemDialogComponent } from '../create-item-dialog/create-item-dialog.component';

@Component({
  standalone: true,
  templateUrl: './item-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ItemListComponent implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = [
    'select',
    'name',
    'tags',
    'start',
    'duration',
    'type',
    'actions',
  ];

  tableTrackerFn = (index: number, item: TimelineItem) => item.id;

  dataSource = new MatTableDataSource<TimelineItem>();
  selection = new TrackBySelectionModel<TimelineItem>(this.tableTrackerFn, true, []);

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private messageService: MessageService,
    private dialog: MatDialog,
  ) {
    title.setTitle('Timeline Items');
    this.refreshData();
  }

  ngAfterViewInit() {
    this.dataSource.sort = this.sort;
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.filteredData.length;
    return numSelected === numRows && numRows > 0;
  }

  masterToggle() {
    this.isAllSelected() ?
      this.selection.clear() :
      this.dataSource.filteredData.forEach(row => this.selection.select(row));
  }

  toggleOne(row: TimelineItem) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  deleteSelectedItems() {
    if (confirm('Are you sure you want to delete the selected items?')) {
      for (const item of this.selection.selected) {
        this.deleteItem(item.id, false);
      }
    }
  }

  deleteItem(id: string, prompt = true) {
    if (!prompt || confirm('Are you sure you want to delete the selected item?'))
      this.yamcs.yamcsClient.deleteTimelineItem(this.yamcs.instance!, id)
        .then(() => this.refreshData())
        .catch(err => this.messageService.showError(err));
  }

  isGroupDeleteEnabled() {
    return !this.selection.isEmpty();
  }

  openCreateItemDialog(type: string) {
    const dialogRef = this.dialog.open(CreateItemDialogComponent, {
      width: '600px',
      panelClass: 'dialog-force-no-scrollbar',
      data: { type },
    });
    dialogRef.afterClosed().subscribe(() => this.refreshData());
  }

  private refreshData() {
    this.yamcs.yamcsClient.getTimelineItems(this.yamcs.instance!, { source: 'rdb' }).then(page => {
      this.selection.matchNewValues(page.items || []);
      this.dataSource.data = page.items || [];
    }).catch(err => this.messageService.showError(err));
  }
}
