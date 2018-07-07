import { AfterViewInit, ChangeDetectionStrategy, Component, ComponentFactoryResolver, ElementRef, OnDestroy, Type, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute } from '@angular/router';
import { Instance } from '@yamcs/client';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { BehaviorSubject } from 'rxjs';
import * as screenfull from 'screenfull';
import { YamcsService } from '../../core/services/YamcsService';
import { ImageViewer } from './ImageViewer';
import { OpiDisplayViewer } from './OpiDisplayViewer';
import { ParameterTableViewer } from './ParameterTableViewer';
import { ParameterTableViewerControls } from './ParameterTableViewerControls';
import { ScriptViewer } from './ScriptViewer';
import { TextViewer } from './TextViewer';
import { UssDisplayViewer } from './UssDisplayViewer';
import { Viewer } from './Viewer';
import { ViewerControlsHost } from './ViewerControlsHost';
import { ViewerHost } from './ViewerHost';

@Component({
  templateUrl: './DisplayFilePage.html',
  styleUrls: ['./DisplayFilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFilePage implements AfterViewInit, OnDestroy {

  instance: Instance;

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

  fullscreenSupported$ = new BehaviorSubject<boolean>(false);
  fullscreen$ = new BehaviorSubject<boolean>(false);
  fullscreenListener: () => void;

  constructor(
    yamcs: YamcsService,
    private route: ActivatedRoute,
    private componentFactoryResolver: ComponentFactoryResolver,
    title: Title,
  ) {
    this.instance = yamcs.getInstance();
    this.fullscreenListener = () => this.fullscreen$.next(screenfull.isFullscreen);
    screenfull.on('change', this.fullscreenListener);

    const url = this.route.snapshot.url;
    this.objectName = '';
    for (let i = 0; i < url.length; i++) {
      if (i === url.length - 1) {
        this.filename = url[i].path;
        this.folderLink = '/monitor/displays/browse/' + this.objectName;
      }
      this.objectName += (i > 0) ? '/' + url[i].path : url[i].path;
    }

    title.setTitle(this.filename + ' - Yamcs');

    // Preload ACE editor (not done in ViewerHost, because ACE does not seem to work well
    // when inialized from an entryComponent)
    if (!!ace) { // Just some code that hopefully does not get treeshaked
      return;
    }
  }

  ngAfterViewInit() {
    if (this.filename.toLowerCase().endsWith('.uss')) {
      this.viewer = this.createViewer(UssDisplayViewer);
    } else if (this.filename.toLowerCase().endsWith('.opi')) {
      this.viewer = this.createViewer(OpiDisplayViewer);
    } else if (this.filename.toLowerCase().endsWith('.par')) {
      this.viewer = this.createViewer(ParameterTableViewer);
      const controls = this.createViewerControls(ParameterTableViewerControls);
      controls.init(this.viewer as ParameterTableViewer);
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
    return lc.endsWith('.png') || lc.endsWith('.gif' || lc.endsWith('.jpg') || lc.endsWith('jpeg') || lc.endsWith('bmp'));
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
  }
}
