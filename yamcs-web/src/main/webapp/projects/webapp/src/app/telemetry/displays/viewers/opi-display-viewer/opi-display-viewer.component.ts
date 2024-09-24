import { APP_BASE_HREF } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, Inject, OnDestroy, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlarmSeverity, Display, PV, PVProvider, Sample } from '@yamcs/opi';
import { Widget } from '@yamcs/opi/dist/types/Widget';
import { ConfigService, Formatter, MessageService, NamedObjectId, ParameterSubscription, ParameterValue, StorageClient, SubscribedParameterInfo, Synchronizer, WebappSdkModule, YamcsService, utils } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { Viewer } from '../Viewer';
import { OpiDisplayConsoleHandler } from './OpiDisplayConsoleHandler';
import { OpiDisplayFontResolver } from './OpiDisplayFontResolver';
import { OpiDisplayHistoricDataProvider } from './OpiDisplayHistoricDataProvider';
import { OpiDisplayPathResolver } from './OpiDisplayPathResolver';
import { YamcsScriptLibrary } from './YamcsScriptLibrary';

// Legacy namespace. New projects should not make use of this.
// Yamcs Studio maps names under this namespace to an "ops://"
// datasource.
const OPS_NAMESPACE = "MDB:OPS Name";
const OPS_DATASOURCE = "ops://";

// Prefix used in query params to distinguish from non-OPI params
const ARGS_PREFIX = 'args.';

@Component({
  standalone: true,
  selector: 'app-opi-display-viewer',
  template: `
    <div #frameInner class="frame-inner">
      <div #displayContainer class="display-container"></div>
    </div>
  `,
  styleUrl: './opi-display-viewer.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class OpiDisplayViewerComponent implements Viewer, PVProvider, OnDestroy {

  private storageClient: StorageClient;
  private bucket: string;

  // Parent element, used to calculate 100% bounds (excluding scroll size)
  private viewerContainerEl: HTMLDivElement;

  @ViewChild('frameInner')
  private frameInner: ElementRef<HTMLDivElement>;

  @ViewChild('displayContainer', { static: true })
  private displayContainer: ElementRef<HTMLDivElement>;

  private display: Display;

  private parameterSubscription: ParameterSubscription;
  private idMapping: { [key: number]: NamedObjectId; } = {};
  private idInfo: { [key: number]: SubscribedParameterInfo; } = {};

  private pvsByName = new Map<string, PV>();
  private subscriptionDirty = false;
  private syncSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    private synchronizer: Synchronizer,
    private messageService: MessageService,
    @Inject(APP_BASE_HREF) private baseHref: string,
    private configService: ConfigService,
    private formatter: Formatter,
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
          if (data.info) {
            this.idInfo = data.info;
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
              const info = this.idInfo[pval.numericId];
              samples.set(pvName, this.toSample(pval, info));
            }
            this.display.setValues(samples);
          }
        });
      }
    }
  }

  private toSample(pval: ParameterValue, info: SubscribedParameterInfo): Sample {
    const time = utils.toDate(pval.generationTime);
    const severity = this.toAlarmSeverity(pval);
    const sample: Sample = { time, severity, value: undefined };
    if (pval.engValue) { // Can be unset if acquisitionStatus is invalid
      sample.value = utils.convertValue(pval.engValue);
      if (pval.engValue.type === 'ENUMERATED') {
        sample.valueIndex = Number(pval.engValue.sint64Value);
      }
    }
    if (info.units) {
      sample.units = info.units;
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
    this.display.utc = this.formatter.isUTC();
    this.display.imagesPrefix = this.baseHref + 'media/';
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
      let url;
      const qs = `?c=${this.yamcs.context}&range=${this.yamcs.getTimeRange()}`;
      if (evt.path.startsWith('/')) {
        url = `/telemetry/displays/files${evt.path}${qs}`;
      } else {
        url = `/telemetry/displays/files/${currentFolder}${evt.path}${qs}`;
      }
      if (evt.args) {
        for (const k in evt.args) {
          url += '&' + ARGS_PREFIX + encodeURIComponent(k) + '=' + encodeURIComponent(evt.args[k]);
        }
      }
      this.router.navigateByUrl(url);
    });

    this.display.addEventListener('closedisplay', evt => {
      this.router.navigateByUrl(`/telemetry/displays/browse?c=${this.yamcs.context}`);
    });

    this.display.addEventListener('openpv', evt => {
      if (evt.pvName.startsWith('/')) {
        this.router.navigateByUrl(`/telemetry/parameters${evt.pvName}/-/summary?c=${this.yamcs.context}`);
      } else if (evt.pvName.startsWith(OPS_DATASOURCE)) {
        // Find first the qualified name
        this.yamcs.yamcsClient.getParameterById(this.yamcs.instance!, {
          namespace: OPS_NAMESPACE,
          name: evt.pvName.substring(6),
        }).then(response => {
          this.router.navigateByUrl(`/telemetry/parameters${response.qualifiedName}/-/summary?c=${this.yamcs.context}`);
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
    const displayArgs: { [key: string]: string; } = {};
    const queryParams = this.route.snapshot.queryParams;
    for (const param in queryParams) {
      if (param.startsWith('args.')) {
        displayArgs[param.substring('args.'.length)] = queryParams[param];
      }
    }
    const promise = this.display.setSource(objectUrl, displayArgs);
    promise.then(() => {

      // Apply display background to entire pane
      let backgroundColor: string;
      if (!this.display.transparent && this.display.instance) {
        backgroundColor = this.display.instance.backgroundColor.hex();
      } else {
        backgroundColor = 'unset';
      }
      this.frameInner.nativeElement.style.backgroundColor = backgroundColor;

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

  createHistoricalDataProvider(pvName: string, widget: Widget) {
    const { yamcs, synchronizer } = this;
    if (this.configService.getConfig().tmArchive) {
      return new OpiDisplayHistoricDataProvider(
        pvName, widget, yamcs, synchronizer, this.configService);
    }
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
    this.display.destroy();
  }
}
