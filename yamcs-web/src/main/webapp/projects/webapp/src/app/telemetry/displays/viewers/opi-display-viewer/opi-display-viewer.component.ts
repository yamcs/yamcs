import { APP_BASE_HREF } from '@angular/common';
import {
  Component,
  ElementRef,
  Inject,
  OnDestroy,
  ViewChild,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlarmSeverity, Sample } from '@yamcs/opi';
import {
  ConfigService,
  Formatter,
  MessageService,
  NamedObjectId,
  ParameterSubscription,
  ParameterValue,
  StorageClient,
  SubscribedParameterInfo,
  Synchronizer,
  utils,
  WebappSdkModule,
  YamcsService,
} from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { OpiDisplayHistoricDataProvider } from './OpiDisplayHistoricDataProvider';
import { Viewer } from '../Viewer';

// Legacy namespace. New projects should not make use of this.
const OPS_NAMESPACE = 'MDB:OPS Name';
const OPS_DATASOURCE = 'ops://';

const ARGS_PREFIX = 'args.';

@Component({
  selector: 'app-opi-display-viewer',
  template: `
    <div #frameInner class="frame-inner">
      <iframe
        #sandboxFrame
        class="display-sandbox"
        sandbox="allow-scripts allow-modals"
        src="/opi-display.html"
      ></iframe>
    </div>
  `,
  styleUrl: './opi-display-viewer.component.css',
  imports: [WebappSdkModule],
})
export class OpiDisplayViewerComponent implements Viewer, OnDestroy {
  private storageClient: StorageClient;
  private bucket: string;

  private viewerContainerEl: HTMLDivElement;

  @ViewChild('frameInner')
  private frameInner: ElementRef<HTMLDivElement>;

  @ViewChild('sandboxFrame', { static: true })
  private sandboxFrame: ElementRef<HTMLIFrameElement>;

  private parameterSubscription: ParameterSubscription;
  private idMapping: { [key: number]: NamedObjectId } = {};
  private idInfo: { [key: number]: SubscribedParameterInfo } = {};

  private pvNames = new Set<string>();
  private subscriptionDirty = false;
  private syncSubscription: Subscription;
  private historyProviders = new Map<string, OpiDisplayHistoricDataProvider>();

  // Resolved once the sandbox HTML has loaded and is ready to receive messages
  private sandboxReady: Promise<void>;
  private sandboxReadyResolve: () => void;

  // Prefix values for resolving script paths in the outer frame
  private relPrefix = '';
  private absPrefix = '';

  private currentScale = 1;

  private messageListener = (event: MessageEvent) => this.handleMessage(event);

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
    this.sandboxReady = new Promise((resolve) => {
      this.sandboxReadyResolve = resolve;
    });
  }

  setViewerContainerEl(viewerContainerEl: HTMLDivElement) {
    this.viewerContainerEl = viewerContainerEl;
  }

  ngAfterViewInit() {
    const iframe = this.sandboxFrame.nativeElement;
    iframe.addEventListener('load', () => this.sandboxReadyResolve(), {
      once: true,
    });
    window.addEventListener('message', this.messageListener);
  }

  private handleMessage(event: MessageEvent) {
    if (event.source !== this.sandboxFrame.nativeElement.contentWindow) {
      return;
    }
    const msg = event.data;
    if (!msg?.type) {
      return;
    }

    switch (msg.type) {
      case 'subscribe':
        for (const pvName of msg.pvNames as string[]) {
          this.pvNames.add(pvName);
        }
        this.subscriptionDirty = true;
        break;

      case 'unsubscribe':
        for (const pvName of msg.pvNames as string[]) {
          this.pvNames.delete(pvName);
        }
        this.subscriptionDirty = true;
        break;

      case 'write':
        this.writeValue(msg.pvName, msg.value);
        break;

      case 'loadScript':
        this.serveScript(msg.path);
        break;

      case 'opendisplay':
        this.openDisplay(msg.path, msg.args);
        break;

      case 'closedisplay':
        this.router.navigateByUrl(
          `/telemetry/displays/browse?c=${this.yamcs.context}`,
        );
        break;

      case 'openpv':
        this.openPV(msg.pvName);
        break;

      case 'runcommand':
        this.yamcs.yamcsClient
          .issueCommand(
            this.yamcs.instance!,
            this.yamcs.processor!,
            msg.command,
            { args: msg.args },
          )
          .catch((err) => this.messageService.showError(err));
        break;

      case 'runstack':
        this.runStack(msg.path);
        break;

      case 'runprocedure':
        this.yamcs.yamcsClient
          .startProcedure(this.yamcs.instance!, msg.procedure, {
            arguments: msg.args,
          })
          .catch((err) => this.messageService.showError(err));
        break;

      case 'openwebpage': {
        const url: string = msg.url;
        if (url.startsWith('http://') || url.startsWith('https://')) {
          window.open(url, '_blank', 'noopener');
        }
        break;
      }

      case 'displaybackground':
        this.frameInner.nativeElement.style.backgroundColor = msg.color;
        break;

      case 'loadImage':
        this.serveImage(msg.url);
        break;

      case 'historySubscribe': {
        const pvName: string = msg.pvName;
        if (!this.historyProviders.has(pvName)) {
          const provider = new OpiDisplayHistoricDataProvider(
            pvName,
            () =>
              this.postToSandbox({
                type: 'historySamples',
                pvName,
                samples: provider.getSamples(),
              }),
            this.yamcs,
            this.synchronizer,
            this.configService,
          );
          this.historyProviders.set(pvName, provider);
        }
        break;
      }

      case 'historyUnsubscribe': {
        const provider = this.historyProviders.get(msg.pvName);
        if (provider) {
          provider.disconnect();
          this.historyProviders.delete(msg.pvName);
        }
        break;
      }
    }
  }

  private openDisplay(path: string, args?: Record<string, string>) {
    let url: string;
    const qs = `?c=${this.yamcs.context}&range=${this.yamcs.getTimeRange()}`;
    const currentFolder = this.relPrefix.slice(this.absPrefix.length);
    if (path.startsWith('/')) {
      url = `/telemetry/displays/files${path}${qs}`;
    } else {
      url = `/telemetry/displays/files/${currentFolder}${path}${qs}`;
    }
    if (args) {
      for (const k in args) {
        url +=
          '&' +
          ARGS_PREFIX +
          encodeURIComponent(k) +
          '=' +
          encodeURIComponent(args[k]);
      }
    }
    this.router.navigateByUrl(url);
  }

  private openPV(pvName: string) {
    if (pvName.startsWith('/')) {
      this.router.navigateByUrl(
        `/telemetry/parameters${pvName}/-/summary?c=${this.yamcs.context}`,
      );
    } else if (pvName.startsWith(OPS_DATASOURCE)) {
      this.yamcs.yamcsClient
        .getParameterById(this.yamcs.instance!, {
          namespace: OPS_NAMESPACE,
          name: pvName.substring(OPS_DATASOURCE.length),
        })
        .then((response) => {
          this.router.navigateByUrl(
            `/telemetry/parameters${response.qualifiedName}/-/summary?c=${this.yamcs.context}`,
          );
        });
    } else {
      alert(`Can't navigate to PV ${pvName}`);
    }
  }

  private runStack(path: string) {
    const parts = path.split('/');
    const resolvedParts: string[] = [];
    for (const part of parts) {
      if (part === '.') {
        // ignore
      } else if (part === '..') {
        resolvedParts.pop();
      } else {
        resolvedParts.push(part);
      }
    }
    const resolvedPath = resolvedParts.join('/');
    const bucketRoot = this.storageClient.getObjectURL(this.bucket, '');
    if (resolvedPath.startsWith(bucketRoot)) {
      const objectName = resolvedPath.slice(bucketRoot.length);
      this.yamcs.yamcsClient
        .startActivity(this.yamcs.instance!, {
          type: 'STACK',
          args: {
            processor: this.yamcs.processor!,
            bucket: this.bucket,
            stack: objectName,
          },
        })
        .catch((err) => this.messageService.showError(err));
    } else {
      this.messageService.showError('Failed to resolve stack path');
    }
  }

  private writeValue(pvName: string, value: any) {
    let parameter = pvName;
    if (pvName.startsWith(OPS_DATASOURCE)) {
      parameter = OPS_NAMESPACE + '/' + pvName.substring(OPS_DATASOURCE.length);
    }
    this.yamcs.yamcsClient
      .setParameterValue(
        this.yamcs.instance!,
        this.yamcs.processor!,
        parameter,
        { type: 'STRING', stringValue: String(value) },
      )
      .then(() =>
        this.messageService.showInfo(`Parameter ${pvName} set to ${value}`),
      )
      .catch((err) => this.messageService.showError(err));
  }

  private async serveScript(path: string) {
    const url = this.resolveScriptPath(path);
    try {
      const response = await fetch(url, { credentials: 'same-origin' });
      const content = response.ok ? await response.text() : null;
      this.postToSandbox({ type: 'scriptContent', path, content });
    } catch {
      this.postToSandbox({ type: 'scriptContent', path, content: null });
    }
  }

  private async serveImage(url: string) {
    try {
      const response = await fetch(url, { credentials: 'same-origin' });
      if (!response.ok) {
        this.postToSandbox({ type: 'imageData', url, dataUrl: null });
        return;
      }
      const blob = await response.blob();
      const dataUrl = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(reader.result as string);
        reader.onerror = reject;
        reader.readAsDataURL(blob);
      });
      this.postToSandbox({ type: 'imageData', url, dataUrl });
    } catch {
      this.postToSandbox({ type: 'imageData', url, dataUrl: null });
    }
  }

  private resolveScriptPath(path: string): string {
    if (path.startsWith('ys://')) {
      const match = path.match(/ys:\/\/([^/]+)\/(.+)/);
      if (match) {
        return this.storageClient.getObjectURL(match[1], match[2]);
      }
    }
    if (path.startsWith('/')) {
      return this.absPrefix + path.slice(1);
    }
    if (path.startsWith('http://') || path.startsWith('https://')) {
      return path;
    }
    return this.relPrefix + path;
  }

  private updateSubscription() {
    if (this.subscriptionDirty) {
      this.subscriptionDirty = false;

      if (this.parameterSubscription) {
        this.parameterSubscription.cancel();
      }

      const ids: NamedObjectId[] = [];
      for (const pvName of this.pvNames) {
        ids.push(this.getIdForPvName(pvName));
      }

      if (ids.length) {
        this.parameterSubscription =
          this.yamcs.yamcsClient!.createParameterSubscription(
            {
              instance: this.yamcs.instance!,
              processor: this.yamcs.processor!,
              id: ids,
              abortOnInvalid: false,
              sendFromCache: true,
              updateOnExpiration: true,
              action: 'REPLACE',
            },
            (data) => {
              if (data.mapping) {
                this.idMapping = data.mapping;
              }

              const meta: Record<
                string,
                { labels?: string[]; writable: boolean }
              > = {};
              if (data.info) {
                this.idInfo = data.info;
                for (const key in data.info) {
                  const id = data.mapping[key];
                  const info = data.info[key];
                  const pvName = this.pvNameById(id);
                  if (pvName) {
                    meta[pvName] = {
                      labels: info.enumValues?.map((x) => x.label),
                      writable:
                        info.dataSource === 'LOCAL' ||
                        info.dataSource === 'EXTERNAL1' ||
                        info.dataSource === 'EXTERNAL2' ||
                        info.dataSource === 'EXTERNAL3',
                    };
                  }
                }
              }

              const disconnected: string[] = [];
              for (const id of data.invalid || []) {
                const pvName = this.pvNameById(id);
                if (pvName) {
                  disconnected.push(pvName);
                }
              }

              const samples: Record<string, Sample> = {};
              for (const pval of data.values || []) {
                pval.id = this.idMapping[pval.numericId];
                const pvName = this.pvNameById(pval.id);
                if (pvName) {
                  const info = this.idInfo[pval.numericId];
                  samples[pvName] = this.toSample(pval, info);
                }
              }

              this.postToSandbox({
                type: 'pvData',
                samples,
                meta,
                disconnected,
              });
            },
          );
      }
    }
  }

  private getIdForPvName(pvName: string): NamedObjectId {
    if (pvName.startsWith(OPS_DATASOURCE)) {
      return {
        namespace: OPS_NAMESPACE,
        name: pvName.substring(OPS_DATASOURCE.length),
      };
    }
    return { name: pvName };
  }

  private pvNameById(id: NamedObjectId): string | null {
    if (!id) return null;
    if (id.namespace === OPS_NAMESPACE) {
      return OPS_DATASOURCE + id.name;
    }
    return id.name;
  }

  private toSample(
    pval: ParameterValue,
    info: SubscribedParameterInfo,
  ): Sample {
    const time = utils.toDate(pval.generationTime);
    const severity = this.toAlarmSeverity(pval);
    const sample: Sample = { time, severity, value: undefined };
    if (pval.engValue) {
      sample.value = utils.convertValue(pval.engValue);
      if (pval.engValue.type === 'ENUMERATED') {
        sample.valueIndex = Number(pval.engValue.sint64Value);
      } else if (pval.engValue.type === 'BINARY') {
        sample.typeHint = 'BINARY_STRING';
      }
    }
    if (info?.units) {
      sample.units = info.units;
    }
    return sample;
  }

  private toAlarmSeverity(pval: ParameterValue): AlarmSeverity {
    if (
      pval.acquisitionStatus === 'EXPIRED' ||
      pval.acquisitionStatus === 'NOT_RECEIVED' ||
      pval.acquisitionStatus === 'INVALID'
    ) {
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

  public async init(objectName: string) {
    const opiConfig = this.configService.getConfig().opi;
    const objectUrl = this.storageClient.getObjectURL(this.bucket, objectName);
    const idx = objectUrl.lastIndexOf('/') + 1;
    this.relPrefix = objectUrl.substring(0, idx);
    this.absPrefix = this.storageClient.getObjectURL(this.bucket, '');

    const displayArgs: Record<string, string> = {};
    const queryParams = this.route.snapshot.queryParams;
    for (const param in queryParams) {
      if (param.startsWith(ARGS_PREFIX)) {
        displayArgs[param.substring(ARGS_PREFIX.length)] = queryParams[param];
      }
    }

    const [xml] = await Promise.all([
      fetch(objectUrl, { credentials: 'same-origin' }).then((r) =>
        r.ok ? r.text() : Promise.reject(`Failed to load ${objectUrl}`),
      ),
      this.sandboxReady,
    ]);

    this.postToSandbox({
      type: 'init',
      xml,
      relPrefix: this.relPrefix,
      absPrefix: this.absPrefix,
      imagesPrefix: this.baseHref + 'media/',
      mediaPrefix: this.baseHref + 'media/',
      args: displayArgs,
      config: {
        legacyFontSizing: opiConfig.legacyFontSizing ?? false,
        disconnectedColor: opiConfig.disconnectedColor,
        invalidColor: opiConfig.invalidColor,
        majorColor: opiConfig.majorColor,
        minorColor: opiConfig.minorColor,
        utc: this.formatter.utc(),
      },
    });

    this.syncSubscription = this.synchronizer.syncFast(() =>
      this.updateSubscription(),
    );
    this.updateSubscription();
  }

  private postToSandbox(msg: object) {
    this.sandboxFrame.nativeElement.contentWindow?.postMessage(msg, '*');
  }

  public hasPendingChanges() {
    return false;
  }

  public zoomIn() {
    this.currentScale += 0.1;
    this.postToSandbox({ type: 'setScale', scale: this.currentScale });
  }

  public zoomOut() {
    this.currentScale -= 0.1;
    this.postToSandbox({ type: 'setScale', scale: this.currentScale });
  }

  public resetZoom() {
    this.currentScale = 1;
    this.postToSandbox({ type: 'setScale', scale: this.currentScale });
  }

  public fitZoom() {
    if (this.viewerContainerEl) {
      this.postToSandbox({
        type: 'fitZoom',
        containerWidth: this.viewerContainerEl.clientWidth,
        containerHeight: this.viewerContainerEl.clientHeight,
      });
    }
  }

  ngOnDestroy() {
    window.removeEventListener('message', this.messageListener);
    this.syncSubscription?.unsubscribe();
    this.parameterSubscription?.cancel();
    for (const provider of this.historyProviders.values()) {
      provider.disconnect();
    }
    this.historyProviders.clear();
  }
}
