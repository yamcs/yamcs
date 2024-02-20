import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, Type, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { ConfigService, YamcsService } from '@yamcs/webapp-sdk';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { ImageViewer } from './ImageViewer';
import { OpiDisplayViewer } from './OpiDisplayViewer';
import { OpiDisplayViewerControls } from './OpiDisplayViewerControls';
import { ParameterTableViewer } from './ParameterTableViewer';
import { ParameterTableViewerControls } from './ParameterTableViewerControls';
import { ScriptViewer } from './ScriptViewer';
import { ScriptViewerControls } from './ScriptViewerControls';
import { TextViewer } from './TextViewer';
import { Viewer } from './Viewer';
import { ViewerControlsHost } from './ViewerControlsHost';
import { ViewerHost } from './ViewerHost';

@Component({
  templateUrl: './DisplayFilePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFilePage implements AfterViewInit, OnDestroy {

  @ViewChild(ViewerControlsHost)
  private controlsHost: ViewerControlsHost;

  @ViewChild('viewerContainer')
  private viewerContainer: ElementRef;

  @ViewChild(ViewerHost)
  private viewerHost: ViewerHost;

  private viewer: Viewer;

  objectName: string;
  filename: string;
  folderLink: string;

  private prevFilename: string;

  private routerSubscription: Subscription;
  private syncSubscription: Subscription;

  private folderPerInstance: boolean;

  constructor(
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    router: Router,
    private title: Title,
    configService: ConfigService,
  ) {
    this.folderPerInstance = configService.getConfig().displayFolderPerInstance;
    const initialObject = this.getObjectNameFromUrl();
    this.loadFile(initialObject);
    this.routerSubscription = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe(() => {
      const newObjectName = this.getObjectNameFromUrl();
      if (newObjectName !== this.objectName) {
        this.loadFile(newObjectName, true);
      }
    });

    // Preload ACE editor (not done in ViewerHost, because ACE does not seem to work well
    // when inialized from an entry component)
    if (!!ace) { // Just some code that hopefully does not get treeshaked
      return;
    }
  }

  private getObjectNameFromUrl() {
    const url = this.route.snapshot.url;
    let objectName = '';
    if (this.folderPerInstance) {
      objectName += this.yamcs.instance!;
    }
    for (const segment of url) {
      if (objectName) {
        objectName += '/';
      }
      objectName += segment.path;
    }
    return objectName;
  }

  private getNameWithoutInstance(name: string) {
    if (this.folderPerInstance) {
      const instance = this.yamcs.instance!;
      return name.substr(instance.length);
    } else {
      return name;
    }
  }

  private loadFile(objectName: string, reloadViewer = false) {
    this.objectName = objectName;
    const idx = this.objectName.lastIndexOf('/');
    if (idx === -1) {
      this.folderLink = '/telemetry/displays/browse/';
      this.filename = this.objectName;
    } else {
      const folderWithoutInstance = this.getNameWithoutInstance(this.objectName.substring(0, idx));
      this.folderLink = '/telemetry/displays/browse/' + folderWithoutInstance;
      this.filename = this.objectName.substring(idx + 1);
    }

    this.title.setTitle(this.filename);

    if (reloadViewer) {
      this.ngAfterViewInit();
    }
  }

  ngAfterViewInit() {
    if (this.filename.toLowerCase().endsWith('.opi')) {
      const opiDisplayViewer = this.createViewer(OpiDisplayViewer);
      opiDisplayViewer.setViewerContainerEl(this.viewerContainer.nativeElement);
      const controls = this.createViewerControls(OpiDisplayViewerControls);
      controls.init(opiDisplayViewer as OpiDisplayViewer);
      this.viewer = opiDisplayViewer;
    } else if (this.filename.toLowerCase().endsWith('.par')) {
      const parameterTableViewer = this.createViewer(ParameterTableViewer);
      const controls = this.createViewerControls(ParameterTableViewerControls);
      controls.init(parameterTableViewer as ParameterTableViewer);
      parameterTableViewer.setEnableActions();
      this.viewer = parameterTableViewer;
    } else if (this.isImage()) {
      this.viewer = this.createViewer(ImageViewer);
    } else if (this.filename.toLocaleLowerCase().endsWith('.js')) {
      const scriptViewer = this.createViewer(ScriptViewer);
      const controls = this.createViewerControls(ScriptViewerControls);
      controls.init(scriptViewer as ScriptViewer);
      this.viewer = scriptViewer;
    } else {
      this.viewer = this.createViewer(TextViewer);
    }

    this.viewer.init(this.objectName);
  }

  private createViewer<T extends Viewer>(viewer: Type<T>): T {
    const viewContainerRef = this.viewerHost.viewContainerRef;
    viewContainerRef.clear();
    const componentRef = viewContainerRef.createComponent(viewer);
    return componentRef.instance;
  }

  private createViewerControls<T>(controls: Type<T>) {
    const viewContainerRef = this.controlsHost.viewContainerRef;
    viewContainerRef.clear();
    const componentRef = viewContainerRef.createComponent(controls);
    return componentRef.instance;
  }

  private isImage() {
    const lc = this.filename.toLowerCase();
    return lc.endsWith('.png') || lc.endsWith('.gif') || lc.endsWith('.jpg') || lc.endsWith('jpeg') || lc.endsWith('bmp');
  }

  hasPendingChanges() {
    return this.viewer.hasPendingChanges();
  }

  ngOnDestroy() {
    this.routerSubscription?.unsubscribe();
    this.syncSubscription?.unsubscribe();
  }
}
