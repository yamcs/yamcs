import { MatDialogRef, MatSelectionList, MatSelectionListChange } from '@angular/material';
import { Component, ViewChild, AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef } from '@angular/core';
import { State } from '../../app.reducers';
import { Store } from '@ngrx/store';
import { Observable } from 'rxjs/Observable';
import { Instance } from '../../../yamcs-client';
import { selectInstances, selectCurrentInstance } from '../../core/store/instance.selectors';
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

  constructor(
    private dialogRef: MatDialogRef<SelectInstanceDialog>,
    private changeDetector: ChangeDetectorRef,
    private router: Router,
    private store: Store<State>,
  ) {
    this.instances$ = store.select(selectInstances);
  }

  ngAfterViewInit() {
    this.store.select(selectCurrentInstance).subscribe(instance => {
      this.selectionList.options.forEach(option => {
        if (option.value === instance.name) {
          option.selected = true;
        }
      });
      this.changeDetector.detectChanges();

      this.selectionList.selectionChange.subscribe((change: MatSelectionListChange) => {
        this.selectionList.deselectAll();
        change.option.selected = true;
      });
    });
  }

  applySelection() {
    this.store.select(selectCurrentInstance).subscribe(instance => {
      const selectedOption = this.selectionList.selectedOptions.selected[0];
      const newInstance = selectedOption.value;
      this.dialogRef.close();
      if (instance.name !== newInstance) {
        this.router.navigateByUrl(`/monitor/displays?instance=${newInstance}`);
      }
    });
  }
}
