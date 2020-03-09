import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { AlarmSeverity, Display, PV, PVProvider, Sample } from '@yamcs/opi';
import { Subscription } from 'rxjs';
import { NamedObjectId, ParameterSubscription, ParameterValue, StorageClient, Value } from '../../client';
import { MessageService } from '../../core/services/MessageService';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import * as utils from '../../shared/utils';
import { Viewer } from './Viewer';
import { YamcsScriptLibrary } from './YamcsScriptLibrary';

@Component({
  selector: 'app-opi-display-viewer',
  template: `
    <div #displayContainer style="width: 100%; height: 100%"></div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpiDisplayViewer implements Viewer, PVProvider, OnDestroy {

  private storageClient: StorageClient;

  @ViewChild('displayContainer', { static: true })
  private displayContainer: ElementRef;

  private display: Display;

  private parameterSubscription: ParameterSubscription;
  private idMapping: { [key: number]: NamedObjectId; } = {};

  private pvsByName = new Map<string, PV>();
  private subscriptionDirty = false;
  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
    private synchronizer: Synchronizer,
    private messageService: MessageService,
  ) {
    this.storageClient = yamcs.createStorageClient();
  }

  private updateSubscription() {
    if (this.subscriptionDirty) {
      this.subscriptionDirty = false;

      if (this.parameterSubscription) {
        this.parameterSubscription.cancel();
      }

      const ids: NamedObjectId[] = [];
      for (const pvName of this.pvsByName.keys()) {
        ids.push({ name: pvName });
      }

      if (ids.length) {
        this.parameterSubscription = this.yamcs.yamcsClient!.createParameterSubscription({
          instance: this.yamcs.getInstance().name,
          processor: this.yamcs.getInstance().name,
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
          action: 'REPLACE',
        }, data => {
          if (data.mapping) {
            this.idMapping = data.mapping;
          }
          if (data.values && data.values.length) {
            const samples = new Map<string, Sample>();
            for (const pval of data.values) {
              pval.id = this.idMapping[pval.numericId];
              samples.set(pval.id.name, this.toSample(pval));
            }
            this.display.setValues(samples);
          }
        });
      }
    }
  }

  private toSample(pval: ParameterValue): Sample {
    const time = utils.toDate(pval.generationTimeUTC);
    const severity = this.toAlarmSeverity(pval);
    const value = this.unpackValue(pval.engValue);
    return { time, severity, value };
  }

  private toAlarmSeverity(pval: ParameterValue) {
    if (pval.acquisitionStatus === 'EXPIRED'
      || pval.acquisitionStatus == 'NOT_RECEIVED'
      || pval.acquisitionStatus === 'INVALID') {
      return AlarmSeverity.INVALID;
    }

    if (!pval.monitoringResult) {
      return AlarmSeverity.NONE;
    }

    switch (pval.monitoringResult) {
      case 'DISABLED':
      case 'IN_LIMITS':
        return AlarmSeverity.NONE;
      case 'WATCH':
      case 'WARNING':
      case 'DISTRESS':
        return AlarmSeverity.MINOR;
      case 'CRITICAL':
      case 'SEVERE':
        return AlarmSeverity.MAJOR;
    }
  }

  private unpackValue(value: Value) {
    switch (value.type) {
      case 'FLOAT':
        return value.floatValue;
      case 'DOUBLE':
        return value.doubleValue;
      case 'UINT32':
        return value.uint32Value;
      case 'SINT32':
        return value.sint32Value;
      case 'UINT64':
        return value.uint64Value;
      case 'SINT64':
        return value.sint64Value;
      case 'BOOLEAN':
        return value.booleanValue;
      case 'TIMESTAMP':
        return value.timestampValue;
      case 'BINARY':
        return window.atob(value.binaryValue!);
      case 'STRING':
        return value.stringValue;
      default:
        throw new Error(`Unexpected value type ${value.type}`);
    }
  }

  /**
   * Don't call before ngAfterViewInit()
   */
  public init(objectName: string) {
    const instance = this.yamcs.getInstance().name;
    const container: HTMLDivElement = this.displayContainer.nativeElement;
    this.display = new Display(container);

    let currentFolder = '';
    if (objectName.lastIndexOf('/') !== -1) {
      currentFolder = objectName.substring(0, objectName.lastIndexOf('/') + 1);
    }

    this.display.addScriptLibrary('Yamcs', new YamcsScriptLibrary(
      this.yamcs, this.messageService, instance));

    this.display.addEventListener('opendisplay', evt => {
      this.router.navigateByUrl(`/telemetry/displays/files/${currentFolder}${evt.path}?instance=${instance}`);
    });

    this.display.addEventListener('closedisplay', evt => {
      this.router.navigateByUrl(`/telemetry/displays/browse?instance=${instance}`);
    });

    this.display.addEventListener('openpv', evt => {
      const encoded = encodeURIComponent(evt.pvName);
      this.router.navigateByUrl(`/telemetry/parameters/${encoded}/summary?instance=${instance}`);
    });

    this.display.addProvider(this);

    const objectUrl = this.storageClient.getObjectURL('_global', 'displays', objectName);

    const idx = objectUrl.lastIndexOf('/') + 1;
    this.display.baseUrl = objectUrl.substring(0, idx);
    const promise = this.display.setSource(objectUrl.substring(idx));
    promise.then(() => {
      this.syncSubscription = this.synchronizer.sync(() => this.updateSubscription());
    });
    return promise;
  }

  canProvide(pvName: string): boolean {
    return pvName.startsWith('/');
  }

  startProviding(pvs: PV[]): void {
    for (const pv of pvs) {
      this.pvsByName.set(pv.name, pv);
    }
    this.subscriptionDirty = true;
  }

  stopProviding(pvs: PV[]): void {
    for (const pv of pvs) {
      this.pvsByName.delete(pv.name);
    }
    this.subscriptionDirty = true;
  }

  isNavigable() {
    return true;
  }

  shutdown() {
  }

  public hasPendingChanges() {
    return false;
  }

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
    if (this.parameterSubscription) {
      this.parameterSubscription.cancel();
    }
  }
}
