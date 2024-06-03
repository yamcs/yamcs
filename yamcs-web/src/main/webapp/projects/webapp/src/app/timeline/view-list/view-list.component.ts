import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { Title } from '@angular/platform-browser';
import { MessageService, TimelineView, TrackBySelectionModel, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './view-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ViewListComponent implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = [
    'select',
    'name',
    'description',
    'actions',
  ];

  tableTrackerFn = (index: number, view: TimelineView) => view.id;

  dataSource = new MatTableDataSource<TimelineView>();
  selection = new TrackBySelectionModel<TimelineView>(this.tableTrackerFn, true, []);

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private messageService: MessageService,
  ) {
    title.setTitle('Timeline Views');
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

  toggleOne(row: TimelineView) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  deleteSelectedViews() {
    if (confirm('Are you sure you want to delete the selected views?')) {
      for (const view of this.selection.selected) {
        this.deleteView(view.id, false);
      }
    }
  }

  deleteView(id: string, prompt = true) {
    if (!prompt || confirm('Are you sure you want to delete the selected view?'))
      this.yamcs.yamcsClient.deleteTimelineView(this.yamcs.instance!, id)
        .then(() => this.refreshData())
        .catch(err => this.messageService.showError(err));
  }

  isGroupDeleteEnabled() {
    return !this.selection.isEmpty();
  }

  private refreshData() {
    this.yamcs.yamcsClient.getTimelineViews(this.yamcs.instance!).then(page => {
      this.selection.matchNewValues(page.views || []);
      this.dataSource.data = page.views || [];
    }).catch(err => this.messageService.showError(err));
  }
}
