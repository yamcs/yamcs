import { ChangeDetectionStrategy, Component, forwardRef } from '@angular/core';
import { ControlValueAccessor, NG_VALUE_ACCESSOR } from '@angular/forms';
import { MatTableDataSource } from '@angular/material/table';
import { TimelineBand, TimelineBandsPage, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  selector: 'app-band-multi-select',
  templateUrl: './band-multi-select.component.html',
  styleUrl: './band-multi-select.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    {
      provide: NG_VALUE_ACCESSOR,
      useExisting: forwardRef(() => BandMultiSelectComponent),
      multi: true
    }
  ],
  imports: [
    WebappSdkModule,
  ],
})
export class BandMultiSelectComponent implements ControlValueAccessor {

  displayedColumns = ['name'];

  availableDataSource = new MatTableDataSource<TimelineBand>([]);
  selectedDataSource = new MatTableDataSource<TimelineBand>([]);
  selectedBand: TimelineBand;

  bands$: Promise<TimelineBandsPage>;

  private onChange = (_: TimelineBand[]) => { };

  constructor(yamcs: YamcsService) {
    this.bands$ = yamcs.yamcsClient.getTimelineBands(yamcs.instance!);
    this.bands$.then(page => {
      this.availableDataSource.data = page.bands || [];
    });
  }

  writeValue(value: any) {
    this.bands$.then(page => { // Make sure bands are loaded
      const allBands = page.bands || [];
      if (value) {
        const selectedIds: string[] = value.map((band: TimelineBand) => band.id);
        const leftBands: TimelineBand[] = [];
        const rightBands: TimelineBand[] = [];
        for (const band of allBands) {
          if (selectedIds.indexOf(band.id) === -1) {
            leftBands.push(band);
          }
        }
        // Another loop, because we really want the right table
        // to preserve order within the view.
        for (const id of selectedIds) {
          for (const band of allBands) {
            if (band.id === id) {
              rightBands.push(band);
            }
          }
        }
        this.availableDataSource.data = leftBands;
        this.selectedDataSource.data = rightBands;
      } else {
        this.availableDataSource.data = [...allBands];
        this.selectedDataSource.data = [];
      }
    });
  }

  registerOnChange(fn: any) {
    this.onChange = fn;
  }

  registerOnTouched(fn: any) {
  }

  selectBand(row: TimelineBand) {
    this.selectedBand = row;
  }

  isLeftSelected(row: TimelineBand) {
    return row === this.selectedBand &&
      this.availableDataSource.data.indexOf(row) !== -1;
  }

  isAnyLeftSelected() {
    return this.selectedBand &&
      this.availableDataSource.data.indexOf(this.selectedBand) !== -1;
  }

  isRightSelected(row: TimelineBand) {
    return row === this.selectedBand &&
      this.selectedDataSource.data.indexOf(row) !== -1;
  }

  isAnyRightSelected() {
    return this.selectedBand &&
      this.selectedDataSource.data.indexOf(this.selectedBand) !== -1;
  }

  moveRight() {
    const row = this.selectedBand;
    const leftData = [...this.availableDataSource.data];
    const rightData = [...this.selectedDataSource.data];
    const leftIndex = leftData.indexOf(row);
    if (leftIndex !== -1) {
      leftData.splice(leftIndex, 1);
    }
    if (rightData.indexOf(row) === -1) {
      rightData.push(row);
    }
    this.availableDataSource.data = leftData;
    this.selectedDataSource.data = rightData;
    this.onChange(rightData);
  }

  moveLeft() {
    const row = this.selectedBand;
    const leftData = [...this.availableDataSource.data];
    const rightData = [...this.selectedDataSource.data];
    const rightIndex = rightData.indexOf(row);
    if (rightIndex !== -1) {
      rightData.splice(rightIndex, 1);
    }
    if (leftData.indexOf(row) === -1) {
      leftData.push(row);
    }
    this.availableDataSource.data = leftData;
    this.selectedDataSource.data = rightData;
    this.onChange(rightData);
  }

  moveUp() {
    const x = this.selectedBand;
    const data = [...this.selectedDataSource.data];
    const index = data.indexOf(x);
    if (index !== 0) {
      data[index] = data[index - 1];
      data[index - 1] = x;
    }
    this.selectedDataSource.data = data;
    this.onChange(data);
  }

  moveDown() {
    const x = this.selectedBand;
    const data = [...this.selectedDataSource.data];
    const index = data.indexOf(x);
    if (index !== data.length - 1) {
      data[index] = data[index + 1];
      data[index + 1] = x;
    }
    this.selectedDataSource.data = data;
    this.onChange(data);
  }
}
