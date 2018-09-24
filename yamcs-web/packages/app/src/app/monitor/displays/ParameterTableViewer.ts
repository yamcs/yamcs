import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material';
import { Instance } from '@yamcs/client';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { SelectParameterDialog } from '../../mdb/parameters/SelectParameterDialog';
import { ParameterTableBuffer } from './ParameterTableBuffer';
import { ParameterTable } from './ParameterTableModel';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-parameter-table-viewer',
  templateUrl: './ParameterTableViewer.html',
  styleUrls: ['./ParameterTableViewer.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterTableViewer implements Viewer, OnDestroy {

  objectName: string;

  selection = new SelectionModel<string>(true, []);

  instance: Instance;

  public model$ = new BehaviorSubject<ParameterTable | null>(null);
  buffer = new ParameterTableBuffer();

  public paused$ = new BehaviorSubject<boolean>(false);
  public hasUnsavedChanges$ = new BehaviorSubject<boolean>(false);
  showActions$ = new BehaviorSubject<boolean>(false);

  private dataSubscription: Subscription;
  private dataSubscriptionId: number;

  constructor(
    private yamcs: YamcsService,
    private changeDetector: ChangeDetectorRef,
    private dialog: MatDialog,
  ) {}

  public init(objectName: string) {
    this.objectName = objectName;
    this.instance = this.yamcs.getInstance();
    this.yamcs.getInstanceClient()!.getObject('displays', objectName).then(response => {
      response.text().then(text => {
        const model: ParameterTable = JSON.parse(text);
        this.model$.next(model);
        this.createOrModifySubscription();
      });
    });
    return Promise.resolve();
  }

  public setEnableActions() {
    this.showActions$.next(true);
  }

  hasPendingChanges() {
    return this.hasUnsavedChanges$.value;
  }

  private createOrModifySubscription() {
    const ids = this.model$.value!.parameters.map(name => ({ name }));
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
          this.buffer.push(pvals);
        });
      });
    }
  }

  public addParameter(name: string) {
    const model = this.model$.value!;
    model.parameters.push(name);
    this.emitModelUpdate(model);
    this.hasUnsavedChanges$.next(true);

    this.createOrModifySubscription();
  }

  /**
   * Forces an update of the model by cloning the instance
   */
  private emitModelUpdate(model: ParameterTable) {
    this.model$.next({
      scroll: model.scroll,
      bufferSize: model.bufferSize || 10,
      columns: model.columns,
      parameters: model.parameters,
    });
  }

  public getModel(): ParameterTable {
    return this.model$.value!;
  }

  public pause() {
    this.paused$.next(true);
  }

  public unpause() {
    this.paused$.next(false);
  }

  public delete() {
    if (!this.selection.isEmpty()) {
      const model = this.model$.value!;
      model.parameters = model.parameters.filter(p => !this.selection.isSelected(p));
      this.emitModelUpdate(model);
      this.hasUnsavedChanges$.next(true);
    }
    this.selection.clear();
  }

  public enableScrollView() {
    this.selection.clear();
    const model = this.model$.value!;
    model.scroll = true;
    this.emitModelUpdate(model);
    this.hasUnsavedChanges$.next(true);
  }

  public enableStandardView() {
    this.selection.clear();
    const model = this.model$.value!;
    model.scroll = false;
    this.emitModelUpdate(model);
    this.hasUnsavedChanges$.next(true);
  }

  public isFullscreenSupported() {
    return false;
  }

  public isScaleSupported() {
    return false;
  }

  removeParameter(name: string) {
    const model = this.model$.value!;
    model.parameters = model.parameters.filter(p => p !== name);
    this.emitModelUpdate(model);
    this.hasUnsavedChanges$.next(true);
  }

  moveUp(index: number) {
    const model = this.model$.value!;
    const x = model.parameters[index];
    if (index === 0) {
      model.parameters[index] = model.parameters[model.parameters.length - 1];
      model.parameters[model.parameters.length - 1] = x;
    } else {
      model.parameters[index] = model.parameters[index - 1];
      model.parameters[index - 1] = x;
    }

    this.emitModelUpdate(model);
    this.hasUnsavedChanges$.next(true);
  }

  moveDown(index: number) {
    const model = this.model$.value!;
    const x = model.parameters[index];
    if (index === model.parameters.length - 1) {
      model.parameters[index] = model.parameters[0];
      model.parameters[0] = x;
    } else {
      model.parameters[index] = model.parameters[index + 1];
      model.parameters[index + 1] = x;
    }

    this.emitModelUpdate(model);
    this.hasUnsavedChanges$.next(true);
  }

  setBufferSize(size: number) {
    this.buffer.setSize(size);
    const model = this.model$.value!;
    model.bufferSize = size;
    this.emitModelUpdate(model);
    this.hasUnsavedChanges$.next(true);
  }

  save() {
    const model = this.model$.value!;
    const b = new Blob([JSON.stringify(model, undefined, 2)]);
    return this.yamcs.getInstanceClient()!.uploadObject('displays', this.objectName, b).then(() => {
      this.hasUnsavedChanges$.next(false);
    });
  }

  showAddParameterDialog() {
    const dialogRef = this.dialog.open(SelectParameterDialog, {
      width: '500px',
      data: {
        okLabel: 'ADD',
        exclude: this.getModel().parameters,
      }
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.addParameter(result);
      }
    });
  }

  ngOnDestroy() {
    if (this.dataSubscription) {
      this.dataSubscription.unsubscribe();
    }
  }
}

