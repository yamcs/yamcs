import { MatDialogRef, MatSelectionList, MatSelectionListChange } from '@angular/material';
import { Component, ViewChild, AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';
import { Instance } from '../../../yamcs-client';
import { selectInstances } from '../../core/store/instance.selectors';
import { Router } from '@angular/router';

@Component({
  selector: 'app-select-instance-dialog',
  templateUrl: './SelectInstanceDialog.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectInstanceDialog implements AfterViewInit {

  @ViewChild(MatSelectionList)
  selectionList: MatSelectionList;

  instances$: Observable<Instance[]>;
  currentInstance: string;

  constructor(
    private dialogRef: MatDialogRef<SelectInstanceDialog>,
    private changeDetector: ChangeDetectorRef,
    private router: Router,
    store: Store<State>,
    yamcs: YamcsService,
  ) {
    this.currentInstance = yamcs.getSelectedInstance().instance;
    this.instances$ = store.select(selectInstances);
  }

  ngAfterViewInit() {
    this.selectionList.options.forEach(option => {
      if (option.value === this.currentInstance) {
        option.selected = true;
      }
    });
    this.changeDetector.detectChanges();

    this.selectionList.selectionChange.subscribe((change: MatSelectionListChange) => {
      this.selectionList.deselectAll();
      change.option.selected = true;
    });
  }

  applySelection() {
    const selectedOption = this.selectionList.selectedOptions.selected[0];
    const newInstance = selectedOption.value;
    this.dialogRef.close();
    if (this.currentInstance !== newInstance) {
      this.router.navigateByUrl(`/monitor?instance=${newInstance}`);
    }
  }
}
