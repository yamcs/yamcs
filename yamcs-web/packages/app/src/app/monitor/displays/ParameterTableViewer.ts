import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { MatTableDataSource } from '@angular/material';
import { Instance, ParameterValue } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { ParameterTable } from './ParameterTableModel';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-parameter-table-viewer',
  templateUrl: './ParameterTableViewer.html',
  styleUrls: ['./ParameterTableViewer.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterTableViewer implements Viewer, OnDestroy {

  path: string;

  dataSource = new MatTableDataSource<Record>([]);
  selection = new SelectionModel<Record>(true, []);

  instance: Instance;

  displayedColumns = [
    'select',
    'severity',
    'name',
    'generationTimeUTC',
    'rawValue',
    'engValue',
    'acquisitionStatus',
    'actions',
  ];

  private latestValues = new Map<string, ParameterValue>();
  private dirty = false;

  public paused$ = new BehaviorSubject<boolean>(false);
  public hasUnsavedChanges$ = new BehaviorSubject<boolean>(false);

  private dataSynchronizer: number;
  private dataSubscription: Subscription;
  private dataSubscriptionId: number;

  public model: ParameterTable;

  constructor(private yamcs: YamcsService, private changeDetector: ChangeDetectorRef) {
    this.dataSynchronizer = window.setInterval(() => {
      if (this.dirty && !this.paused$.value) {
        const data = this.dataSource.data;
        for (const rec of data) {
          rec.pval = this.latestValues.get(rec.name);
        }
        this.dataSource.data = data;
        this.dirty = false;
        this.changeDetector.detectChanges();
      }
    }, 1000 /* update rate */);
  }

  public loadPath(path: string) {
    this.path = path;
    this.instance = this.yamcs.getInstance();
    this.yamcs.getInstanceClient()!.getDisplay(path).then(text => {
      this.model = JSON.parse(text);

      const recs = this.model.parameters.map(name => ({ name }));
      this.dataSource.data = recs;
      this.changeDetector.detectChanges();

      this.createOrModifySubscription();
    });
  }

  hasPendingChanges() {
    return this.hasUnsavedChanges$.value;
  }

  private createOrModifySubscription() {
    const ids = this.model.parameters.map(name => ({ name }));
    if (ids.length) {
      this.yamcs.getInstanceClient()!.getParameterValueUpdates({
        subscriptionId: this.dataSubscriptionId || -1,
        id: ids,
        abortOnInvalid: false,
        sendFromCache: true,
        updateOnExpiration: true,
      }).then(res => {
        this.dataSubscriptionId = res.subscriptionId;
        this.dataSubscription = res.parameterValues$.subscribe(pvals => {
          for (const pval of pvals) {
            this.latestValues.set(pval.id.name, pval);
          }
          this.dirty = true;
        });
      });
    }
  }

  public addParameter(name: string) {
    this.dataSource.data.push({ name });
    this.model.parameters = this.dataSource.data.map(rec => rec.name);
    this.hasUnsavedChanges$.next(true);

    this.changeDetector.detectChanges();
    this.createOrModifySubscription();
  }

  public getParameterNames(): string[] {
    return this.dataSource.data.map(rec => rec.name);
  }

  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource.data.length;
    return numSelected === numRows && numRows > 0;
  }

  masterToggle() {
    this.isAllSelected() ?
        this.selection.clear() :
        this.dataSource.data.forEach(row => this.selection.select(row));
  }

  toggleOne(row: Record) {
    this.selection.clear();
    this.selection.toggle(row);
  }

  public pause() {
    this.paused$.next(true);
  }

  public unpause() {
    this.paused$.next(false);
  }

  public delete() {
    if (!this.selection.isEmpty()) {
      this.dataSource.data = this.dataSource.data.filter(rec => !this.selection.isSelected(rec));

      this.model.parameters = this.dataSource.data.map(rec => rec.name);
      this.hasUnsavedChanges$.next(true);
    }
    this.selection.clear();
  }

  public isFullscreenSupported() {
    return false;
  }

  moveUp(index: number) {
    const data = this.dataSource.data;
    const x = data[index];
    if (index === 0) {
      data[index] = data[data.length - 1];
      data[data.length - 1] = x;
    } else {
      data[index] = data[index - 1];
      data[index - 1] = x;
    }
    this.dataSource.data = data;

    this.model.parameters = data.map(rec => rec.name);
    this.hasUnsavedChanges$.next(true);
  }

  moveDown(index: number) {
    const data = this.dataSource.data;
    const x = data[index];
    if (index === data.length - 1) {
      data[index] = data[0];
      data[0] = x;
    } else {
      data[index] = data[index + 1];
      data[index + 1] = x;
    }
    this.dataSource.data = data;

    this.model.parameters = data.map(rec => rec.name);
    this.hasUnsavedChanges$.next(true);
  }

  save() {
    this.hasUnsavedChanges$.next(false);
    return this.yamcs.getInstanceClient()!.saveDisplay(this.path, this.model);
  }

  ngOnDestroy() {
    if (this.dataSubscription) {
      this.dataSubscription.unsubscribe();
    }
    if (this.dataSynchronizer) {
      window.clearInterval(this.dataSynchronizer);
    }
  }
}

interface Record {
  name: string;
  pval?: ParameterValue;
}
