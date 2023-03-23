import { AfterViewInit, ChangeDetectionStrategy, Component, ViewChild } from '@angular/core';
import { MatLegacyTableDataSource } from '@angular/material/legacy-table';
import { MatSort } from '@angular/material/sort';
import { Title } from '@angular/platform-browser';
import { TimelineBand } from '../client/types/timeline';
import { MessageService } from '../core/services/MessageService';
import { YamcsService } from '../core/services/YamcsService';
import { TrackBySelectionModel } from '../shared/table/TrackBySelectionModel';

@Component({
  templateUrl: './BandsPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BandsPage implements AfterViewInit {

  @ViewChild(MatSort)
  sort: MatSort;

  displayedColumns = [
    'select',
    'name',
    'description',
    'tags',
    'type',
    'actions',
  ];

  tableTrackerFn = (index: number, band: TimelineBand) => band.id;

  dataSource = new MatLegacyTableDataSource<TimelineBand>();
  selection = new TrackBySelectionModel<TimelineBand>(this.tableTrackerFn, true, []);

  constructor(
    readonly yamcs: YamcsService,
    title: Title,
    private messageService: MessageService,
  ) {
    title.setTitle('Timeline Bands');
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

  toggleOne(row: TimelineBand) {
    if (!this.selection.isSelected(row) || this.selection.selected.length > 1) {
      this.selection.clear();
    }
    this.selection.toggle(row);
  }

  deleteSelectedBands() {
    if (confirm('Are you sure you want to delete the selected bands?')) {
      for (const band of this.selection.selected) {
        this.deleteBand(band.id, false);
      }
    }
  }

  deleteBand(id: string, prompt = true) {
    if (!prompt || confirm('Are you sure you want to delete the selected band?'))
      this.yamcs.yamcsClient.deleteTimelineBand(this.yamcs.instance!, id)
        .then(() => this.refreshData())
        .catch(err => this.messageService.showError(err));
  }

  isGroupDeleteEnabled() {
    return !this.selection.isEmpty();
  }

  private refreshData() {
    this.yamcs.yamcsClient.getTimelineBands(this.yamcs.instance!).then(page => {
      this.selection.matchNewValues(page.bands || []);
      this.dataSource.data = page.bands || [];
    }).catch(err => this.messageService.showError(err));
  }
}
