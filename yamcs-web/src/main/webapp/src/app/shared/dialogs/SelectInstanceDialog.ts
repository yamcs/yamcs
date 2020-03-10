import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, ViewChild } from '@angular/core';
import { MatDialogRef } from '@angular/material/dialog';
import { MatSelectionList, MatSelectionListChange } from '@angular/material/list';
import { Router } from '@angular/router';
import { Instance } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-select-instance-dialog',
  templateUrl: './SelectInstanceDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectInstanceDialog implements AfterViewInit {

  @ViewChild(MatSelectionList, { static: true })
  selectionList: MatSelectionList;

  instances$: Promise<Instance[]>;

  constructor(
    private dialogRef: MatDialogRef<SelectInstanceDialog>,
    private changeDetector: ChangeDetectorRef,
    private router: Router,
    private yamcs: YamcsService,
  ) {
    this.instances$ = yamcs.yamcsClient.getInstances({
      filter: 'state=running',
    });
  }

  ngAfterViewInit() {
    const instance = this.yamcs.getInstance();

    this.instances$.then(instances => {
      this.changeDetector.detectChanges();
      this.selectionList.options.forEach(option => {
        if (option.value === instance) {
          option.selected = true;
        }
      });
      this.changeDetector.detectChanges();
    });

    this.selectionList.selectionChange.subscribe((change: MatSelectionListChange) => {
      this.selectionList.deselectAll();
      change.option.selected = true;
    });
  }

  applySelection() {
    const instance = this.yamcs.getInstance();
    const selectedOption = this.selectionList.selectedOptions.selected[0];
    const newInstance = selectedOption.value;
    this.dialogRef.close();
    if (instance !== newInstance) {
      this.router.navigateByUrl(`/telemetry/displays/browse?instance=${newInstance}`);
    }
  }
}
