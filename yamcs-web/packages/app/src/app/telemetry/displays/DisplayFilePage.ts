import { AfterViewInit, ChangeDetectionStrategy, Component, ComponentFactoryResolver, ElementRef, OnDestroy, Type, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { BehaviorSubject, Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import * as screenfull from 'screenfull';
import { Synchronizer } from '../../core/services/Synchronizer';
import { YamcsService } from '../../core/services/YamcsService';
import { ImageViewer } from './ImageViewer';
import { OpiDisplayViewer } from './OpiDisplayViewer';
import { ParameterTableViewer } from './ParameterTableViewer';
import { ParameterTableViewerControls } from './ParameterTableViewerControls';
import { ScriptViewer } from './ScriptViewer';
import { TextViewer } from './TextViewer';
import { UssDisplayViewer } from './UssDisplayViewer';
import { UssDisplayViewerControls } from './UssDisplayViewerControls';
import { Viewer } from './Viewer';
import { ViewerControlsHost } from './ViewerControlsHost';
import { ViewerHost } from './ViewerHost';

@Component({
  templateUrl: './DisplayFilePage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFilePage implements AfterViewInit, OnDestroy {

  instance: Instance;

  @ViewChild(ViewerControlsHost, { static: true })
  private controlsHost: ViewerControlsHost;

  @ViewChild('viewerContainer', { static: true })
  private viewerContainer: ElementRef;

  @ViewChild(ViewerHost, { static: true })
  private viewerHost: ViewerHost;

  private viewer: Viewer;

  objectName: string;
  filename: string;
  folderLink: string;

  private prevFilename: string;

  fullscreenSupported$ = new BehaviorSubject<boolean>(false);
  fullscreen$ = new BehaviorSubject<boolean>(false);
  fullscreenListener: () => void;

  private routerSubscription: Subscription;
  private syncSubscription: Subscription;

  constructor(
    yamcs: YamcsService,
    private route: ActivatedRoute,
    router: Router,
    private componentFactoryResolver: ComponentFactoryResolver,
    private title: Title,
    private synchronizer: Synchronizer,
  ) {
    this.instance = yamcs.getInstance();
    this.fullscreenListener = () => this.fullscreen$.next(screenfull.isFullscreen);
    screenfull.on('change', this.fullscreenListener);

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
    // when inialized from an entryComponent)
    if (!!ace) { // Just some code that hopefully does not get treeshaked
      return;
    }
  }

  private getObjectNameFromUrl() {
    const url = this.route.snapshot.url;
    let objectName = '';
    for (let i = 0; i < url.length; i++) {
      objectName += (i > 0) ? '/' + url[i].path : url[i].path;
    }
    return objectName;
  }

  private loadFile(objectName: string, reloadViewer = false) {
    this.objectName = objectName;
    const idx = this.objectName.lastIndexOf('/');
    if (idx === -1) {
      this.folderLink = '/telemetry/displays/browse/';
      this.filename = this.objectName;
    } else {
      this.folderLink = '/telemetry/displays/browse/' + this.objectName.substring(0, idx);
      this.filename = this.objectName.substring(idx);
    }

    this.title.setTitle(this.filename);

    if (reloadViewer) {
      //console.log('force reload');
      ///this.ngAfterViewInit();
    }
  }

  ngAfterViewInit() {
    if (this.filename.toLowerCase().endsWith('.uss')) {
      const ussDisplayViewer = this.createViewer(UssDisplayViewer);
      const controls = this.createViewerControls(UssDisplayViewerControls);
      controls.init(ussDisplayViewer);
      this.viewer = ussDisplayViewer;
      this.syncSubscription = this.synchronizer.syncFast(() => {
        ussDisplayViewer.display.digest();
      });
    } else if (this.filename.toLowerCase().endsWith('.opi')) {
      const opiDisplayViewer = this.createViewer(OpiDisplayViewer);
      this.viewer = opiDisplayViewer;
      this.syncSubscription = this.synchronizer.syncFast(() => {
        opiDisplayViewer.display.digest();
      });
    } else if (this.filename.toLowerCase().endsWith('.par')) {
      const parameterTableViewer = this.createViewer(ParameterTableViewer);
      const controls = this.createViewerControls(ParameterTableViewerControls);
      controls.init(parameterTableViewer as ParameterTableViewer);
      parameterTableViewer.setEnableActions();
      this.viewer = parameterTableViewer;
    } else if (this.isImage()) {
      this.viewer = this.createViewer(ImageViewer);
    } else if (this.filename.toLocaleLowerCase().endsWith('.js')) {
      this.viewer = this.createViewer(ScriptViewer);
    } else {
      this.viewer = this.createViewer(TextViewer);
    }

    this.viewer.init(this.objectName);
    this.fullscreenSupported$.next(this.viewer.isFullscreenSupported());
  }

  private createViewer<T extends Viewer>(viewer: Type<T>): T {
    const componentFactory = this.componentFactoryResolver.resolveComponentFactory(viewer);
    const viewContainerRef = this.viewerHost.viewContainerRef;
    viewContainerRef.clear();
    const componentRef = viewContainerRef.createComponent(componentFactory);
    return componentRef.instance;
  }

  private createViewerControls<T>(controls: Type<T>) {
    const componentFactory = this.componentFactoryResolver.resolveComponentFactory(controls);
    const viewContainerRef = this.controlsHost.viewContainerRef;
    viewContainerRef.clear();
    const componentRef = viewContainerRef.createComponent(componentFactory);
    return componentRef.instance;
  }

  private isImage() {
    const lc = this.filename.toLowerCase();
    return lc.endsWith('.png') || lc.endsWith('.gif') || lc.endsWith('.jpg') || lc.endsWith('jpeg') || lc.endsWith('bmp');
  }

  goFullscreen() {
    if (screenfull.enabled) {
      screenfull.request(this.viewerContainer.nativeElement);
    } else {
      alert('Your browser does not appear to support going full screen');
    }
  }

  hasPendingChanges() {
    return this.viewer.hasPendingChanges();
  }

  ngOnDestroy() {
    screenfull.off('change', this.fullscreenListener);
    if (this.routerSubscription) {
      this.routerSubscription.unsubscribe();
    }
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
