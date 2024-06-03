import { SelectionModel } from '@angular/cdk/collections';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { ConfigService, NamedObjectId, ParameterSubscription, StorageClient, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { SelectParameterDialogComponent } from '../../../../shared/select-parameter-dialog/select-parameter-dialog.component';
import { Viewer } from '../Viewer';
import { ParameterTableBuffer } from './ParameterTableBuffer';
import { ParameterTable } from './ParameterTableModel';
import { MultipleParameterTableComponent } from './multiple-parameter-table/multiple-parameter-table.component';
import { ScrollingParameterTable } from './scrolling-parameter-table/scrolling-parameter-table.component';

@Component({
  standalone: true,
  selector: 'app-parameter-table-viewer',
  templateUrl: './parameter-table-viewer.component.html',
  styleUrl: './parameter-table-viewer.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MultipleParameterTableComponent,
    ScrollingParameterTable,
    WebappSdkModule,
  ],
})
export class ParameterTableViewerComponent implements Viewer, OnDestroy {

  objectName: string;

  selection = new SelectionModel<string>(true, []);

  private storageClient: StorageClient;
  private bucket: string;

  public model$ = new BehaviorSubject<ParameterTable | null>(null);
  buffer = new ParameterTableBuffer();

  public paused$ = new BehaviorSubject<boolean>(false);
  public hasUnsavedChanges$ = new BehaviorSubject<boolean>(false);
  showActions$ = new BehaviorSubject<boolean>(false);

  private dataSubscription: ParameterSubscription;
  private idMapping: { [key: number]: NamedObjectId; };

  constructor(
    private yamcs: YamcsService,
    private dialog: MatDialog,
    configService: ConfigService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();
  }

  public init(objectName: string) {
    this.objectName = objectName;
    this.storageClient.getObject(this.bucket, objectName).then(response => {
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
      if (this.dataSubscription) {
        this.dataSubscription.sendMessage({
          instance: this.yamcs.instance!,
          processor: this.yamcs.processor!,
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
          action: 'REPLACE',
        });
      } else {
        this.dataSubscription = this.yamcs.yamcsClient.createParameterSubscription({
          instance: this.yamcs.instance!,
          processor: this.yamcs.processor!,
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
          action: 'REPLACE',
        }, data => {
          if (data.mapping) {
            this.idMapping = data.mapping;
          }
          const pvals = data.values || [];
          this.buffer.push(pvals, this.idMapping);
        });
      }
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
    const b = new Blob([JSON.stringify({
      "$schema": "https://yamcs.org/schema/parameter-table.schema.json",
      ...this.model$.value!,
    }, undefined, 2)], {
      type: 'application/json'
    });
    return this.storageClient.uploadObject(this.bucket, this.objectName, b).then(() => {
      this.hasUnsavedChanges$.next(false);
    });
  }

  showAddParameterDialog() {
    const dialogRef = this.dialog.open(SelectParameterDialogComponent, {
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
    this.dataSubscription?.cancel();
  }
}
