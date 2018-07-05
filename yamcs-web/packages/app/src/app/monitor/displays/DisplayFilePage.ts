import { AfterViewInit, ChangeDetectionStrategy, Component, ComponentFactoryResolver, ElementRef, OnDestroy, Type, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Instance } from '@yamcs/client';
import * as ace from 'brace';
import 'brace/mode/javascript';
import 'brace/theme/eclipse';
import 'brace/theme/twilight';
import { BehaviorSubject } from 'rxjs';
import * as screenfull from 'screenfull';
import { PreferenceStore } from '../../core/services/PreferenceStore';
import { YamcsService } from '../../core/services/YamcsService';
import { ImageViewer } from './ImageViewer';
import { OpiDisplayViewer } from './OpiDisplayViewer';
import { ParameterTableViewer } from './ParameterTableViewer';
import { ScriptViewer } from './ScriptViewer';
import { TextViewer } from './TextViewer';
import { UssDisplayViewer } from './UssDisplayViewer';
import { Viewer } from './Viewer';
import { ViewerHost } from './ViewerHost';

@Component({
  templateUrl: './DisplayFilePage.html',
  styleUrls: ['./DisplayFilePage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DisplayFilePage implements AfterViewInit, OnDestroy {

  instance: Instance;

  @ViewChild('viewerContainer')
  private viewerContainer: ElementRef;

  @ViewChild(ViewerHost)
  private viewerHost: ViewerHost;

  path: string;
  filename: string;
  folderLink: string;

  fullscreenSupported$ = new BehaviorSubject<boolean>(false);
  fullscreen$ = new BehaviorSubject<boolean>(false);
  fullscreenListener: () => void;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
    private route: ActivatedRoute,
    private preferenceStore: PreferenceStore,
    private componentFactoryResolver: ComponentFactoryResolver,
  ) {
    this.instance = yamcs.getInstance();
    this.fullscreenListener = () => this.fullscreen$.next(screenfull.isFullscreen);
    screenfull.on('change', this.fullscreenListener);

    const url = this.route.snapshot.url;
    let path = '';
    for (let i = 0; i < url.length; i++) {
      if (i === url.length - 1) {
        this.filename = url[i].path;
        this.folderLink = '/monitor/displays/browse' + path;
      }
      path += '/' + url[i].path;
    }
    this.path = path;


    console.log('preloading ace', ace);
  }

  ngAfterViewInit() {
    const container: HTMLDivElement = this.viewerContainer.nativeElement;
    const instance = this.instance.name;
    const url = `${this.yamcs.yamcsClient.staticUrl}/${instance}/displays${this.path}`;

    let viewer: Viewer;
    if (this.filename.toLowerCase().endsWith('.uss')) {
      viewer = this.createViewer(UssDisplayViewer);
    } else if (this.filename.toLowerCase().endsWith('.opi')) {
      viewer = this.createViewer(OpiDisplayViewer);
    } else if (this.filename.toLowerCase().endsWith('.par')) {
      viewer = this.createViewer(ParameterTableViewer);
    } else if (this.isImage()) {
      viewer = this.createViewer(ImageViewer);
    } else if (this.filename.toLocaleLowerCase().endsWith('.js')) {
      viewer = this.createViewer(ScriptViewer);
    } else {
      viewer = this.createViewer(TextViewer);
    }

    viewer.loadPath(this.path);
    this.fullscreenSupported$.next(viewer.isFullscreenSupported());
  }

  private createViewer<T extends Viewer>(viewer: Type<T>): T {
    const componentFactory = this.componentFactoryResolver.resolveComponentFactory(viewer);
    const viewContainerRef = this.viewerHost.viewContainerRef;
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

  ngOnDestroy() {
    screenfull.off('change', this.fullscreenListener);
  }
}
