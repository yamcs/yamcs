import { SelectionModel } from '@angular/cdk/collections';
import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
} from '@angular/core';
import { UntypedFormControl } from '@angular/forms';
import { MatDialogRef } from '@angular/material/dialog';
import { MatTableDataSource } from '@angular/material/table';
import { TimelineBand, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  selector: 'app-select-band-dialog',
  templateUrl: './select-band-dialog.component.html',
  styleUrl: './select-band-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class SelectBandDialogComponent implements AfterViewInit {
  filterControl = new UntypedFormControl();

  dataSource = new MatTableDataSource<TimelineBand>([]);
  selection = new SelectionModel<TimelineBand>();

  displayedColumns = ['name', 'description', 'type', 'actions'];

  constructor(
    private dialogRef: MatDialogRef<SelectBandDialogComponent, TimelineBand>,
    readonly yamcs: YamcsService,
  ) {
    this.dataSource.filterPredicate = (band, filter) => {
      return band.name.toLowerCase().indexOf(filter) >= 0;
    };

    yamcs.yamcsClient.getTimelineBands(this.yamcs.instance!).then((page) => {
      this.dataSource.data = page.bands || [];
    });
  }

  ngAfterViewInit() {
    this.filterControl.valueChanges.subscribe(() => {
      const value = this.filterControl.value || '';
      this.dataSource.filter = value.toLowerCase();

      if (this.selection.hasValue()) {
        const item = this.selection.selected[0];
        if (this.dataSource.filteredData.indexOf(item) === -1) {
          this.selection.clear();
        }
      }
    });
  }

  selectNext() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.min(items.indexOf(currentItem) + 1, items.length - 1);
      }
    }
    this.selection.select(items[idx]);
  }

  selectPrevious() {
    const items = this.dataSource.filteredData;
    let idx = 0;
    if (this.selection.hasValue()) {
      const currentItem = this.selection.selected[0];
      if (items.indexOf(currentItem) !== -1) {
        idx = Math.max(items.indexOf(currentItem) - 1, 0);
      }
    }
    this.selection.select(items[idx]);
  }

  applySelection() {
    const selected = this.selection.selected;
    if (selected.length) {
      const selectedBand = this.selection.selected[0];
      this.selectBand(selectedBand);
    }
  }

  selectBand(band: TimelineBand) {
    this.dialogRef.close(band);
  }
}
