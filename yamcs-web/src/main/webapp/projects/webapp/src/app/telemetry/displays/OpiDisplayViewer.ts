import { APP_BASE_HREF } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, Inject, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { AlarmSeverity, Display, PV, PVProvider, Sample } from '@yamcs/opi';
import { ConfigService, MessageService, NamedObjectId, ParameterSubscription, ParameterValue, StorageClient, Synchronizer, YamcsService, utils } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { OpiDisplayConsoleHandler } from './OpiDisplayConsoleHandler';
import { OpiDisplayFontResolver } from './OpiDisplayFontResolver';
import { OpiDisplayPathResolver } from './OpiDisplayPathResolver';
import { Viewer } from './Viewer';
import { YamcsScriptLibrary } from './YamcsScriptLibrary';

// Legacy namespace. New projects should not make use of this.
// Yamcs Studio maps names under this namespace to an "ops://"
// datasource.
const OPS_NAMESPACE = "MDB:OPS Name";
const OPS_DATASOURCE = "ops://";

@Component({
  selector: 'app-opi-display-viewer',
  template: `
    <div class="display-frame">
      <div class="display-frame-inner">
        <div #displayContainer style="display: inline-block"></div>
      </div>
    </div>
  `,
  styleUrls: ['./OpiDisplayViewer.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpiDisplayViewer implements Viewer, PVProvider, OnDestroy {

  private storageClient: StorageClient;
  private bucket: string;

  // Parent element, used to calculate 100% bounds (excluding scroll size)
  private viewerContainerEl: HTMLDivElement;

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
    @Inject(APP_BASE_HREF) private baseHref: string,
    configService: ConfigService,
  ) {
    this.storageClient = yamcs.createStorageClient();
    this.bucket = configService.getDisplayBucket();
  }

  setViewerContainerEl(viewerContainerEl: HTMLDivElement) {
    this.viewerContainerEl = viewerContainerEl;
  }

  private updateSubscription() {
    if (this.subscriptionDirty) {
      this.subscriptionDirty = false;

      if (this.parameterSubscription) {
        this.parameterSubscription.cancel();
      }

      const ids: NamedObjectId[] = [];
      for (const pvName of this.pvsByName.keys()) {
        if (pvName.startsWith(OPS_DATASOURCE)) { // Legacy
          ids.push({ namespace: OPS_NAMESPACE, name: pvName.substr(6) });
        } else {
          ids.push({ name: pvName });
        }
      }

      if (ids.length) {
        this.parameterSubscription = this.yamcs.yamcsClient!.createParameterSubscription({
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
          for (const id of (data.invalid || [])) {
            let pvName = id.name;
            if (id.namespace === OPS_NAMESPACE) {
              pvName = OPS_DATASOURCE + pvName;
            }
            const pv = this.display.getPV(pvName);
            if (pv) {
              pv.disconnected = true;
            }
          }
          if (data.values?.length) {
            const samples = new Map<string, Sample>();
            for (const pval of data.values) {
              pval.id = this.idMapping[pval.numericId];
              let pvName = pval.id.name;
              if (pval.id.namespace === OPS_NAMESPACE) {
                pvName = OPS_DATASOURCE + pvName;
              }
              samples.set(pvName, this.toSample(pval));
            }
            this.display.setValues(samples);
          }
        });
      }
    }
  }

  private toSample(pval: ParameterValue): Sample {
    const time = utils.toDate(pval.generationTime);
    const severity = this.toAlarmSeverity(pval);
    const sample: Sample = { time, severity, value: undefined };
    if (pval.engValue) { // Can be unset if acquisitionStatus is invalid
      sample.value = utils.convertValue(pval.engValue);
      if (pval.engValue.type === 'ENUMERATED') {
        sample.valueIndex = Number(pval.engValue.sint64Value);
      }
    }
    return sample;
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

  /**
   * Don't call before ngAfterViewInit()
   */
  public init(objectName: string) {
    const container: HTMLDivElement = this.displayContainer.nativeElement;
    this.display = new Display(container);
    this.display.imagesPrefix = this.baseHref;
    this.display.setPathResolver(new OpiDisplayPathResolver(this.storageClient, this.display));
    this.display.setConsoleHandler(new OpiDisplayConsoleHandler(this.messageService));
    this.display.setFontResolver(new OpiDisplayFontResolver(this.baseHref));

    let currentFolder = '';
    if (objectName.lastIndexOf('/') !== -1) {
      currentFolder = objectName.substring(0, objectName.lastIndexOf('/') + 1);
    }

    this.display.addScriptLibrary('Yamcs', new YamcsScriptLibrary(
      this.yamcs, this.messageService));

    this.display.addEventListener('opendisplay', evt => {
      if (evt.path.startsWith('/')) {
        this.router.navigateByUrl(`/telemetry/displays/files${evt.path}?c=${this.yamcs.context}`);
      } else {
        this.router.navigateByUrl(`/telemetry/displays/files/${currentFolder}${evt.path}?c=${this.yamcs.context}`);
      }
    });

    this.display.addEventListener('closedisplay', evt => {
      this.router.navigateByUrl(`/telemetry/displays/browse?c=${this.yamcs.context}`);
    });

    this.display.addEventListener('openpv', evt => {
      if (evt.pvName.startsWith('/')) {
        const encoded = encodeURIComponent(evt.pvName);
        this.router.navigateByUrl(`/telemetry/parameters/${encoded}/summary?c=${this.yamcs.context}`);
      } else if (evt.pvName.startsWith(OPS_DATASOURCE)) {
        // Find first the qualified name
        this.yamcs.yamcsClient.getParameterById(this.yamcs.instance!, {
          namespace: OPS_NAMESPACE,
          name: evt.pvName.substr(6),
        }).then(response => {
          const encoded = encodeURIComponent(response.qualifiedName);
          this.router.navigateByUrl(`/telemetry/parameters/${encoded}/summary?c=${this.yamcs.context}`);
        });
      } else {
        alert(`Can't navigate to PV ${evt.pvName}`);
      }
    });

    this.display.addEventListener('runcommand', evt => {
      this.yamcs.yamcsClient.issueCommand(this.yamcs.instance!, this.yamcs.processor!, evt.command, {
        args: evt.args,
      }).catch(err => this.messageService.showError(err));
    });

    this.display.addEventListener('runprocedure', evt => {
      this.yamcs.yamcsClient.startProcedure(this.yamcs.instance!, evt.procedure, {
        arguments: evt.args,
      }).catch(err => this.messageService.showError(err));
    });

    this.display.addProvider(this);
    this.display.absPrefix = this.storageClient.getObjectURL(this.bucket, '');

    const objectUrl = this.storageClient.getObjectURL(this.bucket, objectName);
    const promise = this.display.setSource(objectUrl);
    promise.then(() => {
      this.syncSubscription = this.synchronizer.sync(() => this.updateSubscription());
    });
    return promise;
  }

  canProvide(pvName: string): boolean {
    return true; // Try it all (we run after defaults)
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

  public zoomIn() {
    this.display.scale += 0.1;
  }

  public zoomOut() {
    this.display.scale -= 0.1;
  }

  public resetZoom() {
    this.display.scale = 1;
  }

  public fitZoom() {
    const displayInstance = this.display.instance;
    if (displayInstance && this.viewerContainerEl) {
      const frameWidth = this.viewerContainerEl.clientWidth;
      const frameHeight = this.viewerContainerEl.clientHeight;

      const { width, height } = displayInstance.unscaledBounds;
      const xScale = frameWidth / width;
      const yScale = frameHeight / height;
      this.display.scale = Math.min(xScale, yScale);
    }
  }

  ngOnDestroy() {
    this.syncSubscription?.unsubscribe();
    this.parameterSubscription?.cancel();
  }
}
