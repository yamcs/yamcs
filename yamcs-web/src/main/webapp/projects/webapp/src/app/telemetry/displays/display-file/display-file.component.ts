import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, OnDestroy, Type, ViewChild } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, NavigationEnd, Router } from '@angular/router';
import { ConfigService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { Viewer } from '../viewers/Viewer';
import { ImageViewerComponent } from '../viewers/image-viewer/image-viewer.component';
import { OpiDisplayViewerControlsComponent } from '../viewers/opi-display-viewer-controls/opi-display-viewer-controls.component';
import { OpiDisplayViewerComponent } from '../viewers/opi-display-viewer/opi-display-viewer.component';
import { ParameterTableViewerControlsComponent } from '../viewers/parameter-table-viewer-controls/parameter-table-viewer-controls.component';
import { ParameterTableViewerComponent } from '../viewers/parameter-table-viewer/parameter-table-viewer.component';
import { ScriptViewerControlsComponent } from '../viewers/script-viewer-controls/script-viewer-controls.component';
import { ScriptViewerComponent } from '../viewers/script-viewer/script-viewer.component';
import { TextViewerComponent } from '../viewers/text-viewer/text-viewer.component';
import { ViewerControlsHostDirective } from './viewer-controls-host.directive';
import { ViewerHostDirective } from './viewer-host.directive';

@Component({
  standalone: true,
  templateUrl: './display-file.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ImageViewerComponent,
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    OpiDisplayViewerComponent,
    OpiDisplayViewerControlsComponent,
    ParameterTableViewerComponent,
    ParameterTableViewerControlsComponent,
    ScriptViewerComponent,
    ScriptViewerControlsComponent,
    WebappSdkModule,
    TextViewerComponent,
    ViewerControlsHostDirective,
    ViewerHostDirective,
  ],
})
export class DisplayFileComponent implements AfterViewInit, OnDestroy {

  @ViewChild(ViewerControlsHostDirective)
  private controlsHost: ViewerControlsHostDirective;

  @ViewChild('viewerContainer')
  private viewerContainer: ElementRef;

  @ViewChild(ViewerHostDirective)
  private viewerHost: ViewerHostDirective;

  private viewer: Viewer;

  objectName: string;
  filename: string;
  folderLink: string;

  private subscriptions: Subscription[] = [];

  constructor(
    readonly yamcs: YamcsService,
    private route: ActivatedRoute,
    router: Router,
    private title: Title,
    configService: ConfigService,
  ) {
    const initialObject = this.getObjectNameFromUrl();
    this.loadFile(initialObject);

    let sub = router.events.pipe(
      filter(evt => evt instanceof NavigationEnd)
    ).subscribe(() => {
      const newObjectName = this.getObjectNameFromUrl();
      if (newObjectName !== this.objectName) {
        this.loadFile(newObjectName, true);
      }
    });
    this.subscriptions.push(sub);

    const preferredRange = route.snapshot.queryParamMap.get('range');
    if (preferredRange) {
      yamcs.range$.next(preferredRange);
    }

    sub = yamcs.range$.subscribe(range => {
      router.navigate([], {
        replaceUrl: true,
        relativeTo: route,
        queryParams: { range },
        queryParamsHandling: 'merge',
      });
    });
    this.subscriptions.push(sub);
  }

  private getObjectNameFromUrl() {
    const url = this.route.snapshot.url;
    let objectName = '';
    for (const segment of url) {
      if (objectName) {
        objectName += '/';
      }
      objectName += segment.path;
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
      const folderName = this.objectName.substring(0, idx);
      this.folderLink = '/telemetry/displays/browse/' + folderName;
      this.filename = this.objectName.substring(idx + 1);
    }

    this.title.setTitle(this.filename);

    if (reloadViewer) {
      this.ngAfterViewInit();
    }
  }

  ngAfterViewInit() {
    if (this.filename.toLowerCase().endsWith('.opi')) {
      const opiDisplayViewer = this.createViewer(OpiDisplayViewerComponent);
      opiDisplayViewer.setViewerContainerEl(this.viewerContainer.nativeElement);
      const controls = this.createViewerControls(OpiDisplayViewerControlsComponent);
      controls.init(opiDisplayViewer as OpiDisplayViewerComponent);
      this.viewer = opiDisplayViewer;
    } else if (this.filename.toLowerCase().endsWith('.par')) {
      const parameterTableViewer = this.createViewer(ParameterTableViewerComponent);
      const controls = this.createViewerControls(ParameterTableViewerControlsComponent);
      controls.init(parameterTableViewer as ParameterTableViewerComponent);
      parameterTableViewer.setEnableActions();
      this.viewer = parameterTableViewer;
    } else if (this.isImage()) {
      this.viewer = this.createViewer(ImageViewerComponent);
    } else if (this.filename.toLocaleLowerCase().endsWith('.js')) {
      const scriptViewer = this.createViewer(ScriptViewerComponent);
      const controls = this.createViewerControls(ScriptViewerControlsComponent);
      controls.init(scriptViewer as ScriptViewerComponent);
      this.viewer = scriptViewer;
    } else {
      this.viewer = this.createViewer(TextViewerComponent);
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
    this.subscriptions.forEach(x => x.unsubscribe());
  }
}
