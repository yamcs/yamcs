import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Display, NavigationHandler, OpenDisplayCommandOptions, UssDisplay } from '@yamcs/displays';
import { BehaviorSubject, Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from './MyDisplayCommunicator';
import { Viewer } from './Viewer';

/**
 * Simple nav handler that relocates the browser to the standalone display pages.
 */
export class DefaultNavigationHandler implements NavigationHandler {

  constructor(
    private objectName: string,
    private instance: string,
    private router: Router,
  ) {}

  getBaseId() {
    return this.objectName;
  }

  openDisplay(options: OpenDisplayCommandOptions) {
    this.router.navigateByUrl(`/monitor/displays/files/${options.target}?instance=${this.instance}`);
  }

  closeDisplay() {
    this.router.navigateByUrl(`monitor/displays/browse?instance=${this.instance}`);
  }
}

@Component({
  selector: 'app-uss-display-viewer',
  template: `
    <div #wrapper class="wrapper" [class.center]="center$ | async">
      <div #displayContainer style="line-height: 0"></div>
    </div>
  `,
  styles: [`
    .wrapper.center {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%,-50%);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UssDisplayViewer implements Viewer, OnDestroy {

  @ViewChild('wrapper')
  private wrapper: ElementRef;

  @ViewChild('displayContainer')
  private displayContainer: ElementRef;

  private objectName: string;

  center$ = new BehaviorSubject<boolean>(false);

  private zoom = 1;

  display: Display;

  private parameterSubscription: Subscription;

  private navigationHandler: NavigationHandler;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
  ) {}

  /**
   * Don't call before ngAfterViewInit()
   */
  public init(objectName: string, navigationHandler?: NavigationHandler) {
    this.objectName = objectName;

    if (navigationHandler) {
      this.navigationHandler = navigationHandler;
    } else {
      const instance = this.yamcs.getInstance().name;
      this.navigationHandler = new DefaultNavigationHandler(objectName, instance, this.router);
    }

    const container: HTMLDivElement = this.displayContainer.nativeElement;
    const displayCommunicator = new MyDisplayCommunicator(this.yamcs, this.router);
    this.display = new UssDisplay(this.navigationHandler, container, displayCommunicator);
    return this.display.parseAndDraw(this.objectName).then(() => {
      const ids = this.display.getParameterIds();
      if (ids.length) {
        this.yamcs.getInstanceClient()!.getParameterValueUpdates({
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
        }).then(res => {
          this.parameterSubscription = res.parameterValues$.subscribe(pvals => {
            this.display.processParameterValues(pvals);
          });
        });
      }
    });
  }

  public setCenterContent(center: true) {
    this.center$.next(center);
  }

  public isFullscreenSupported() {
    return true;
  }

  public hasPendingChanges() {
    return false;
  }

  public zoomIn() {
    this.zoom += this.zoom * 0.3;
    this.wrapper.nativeElement.style.setProperty('zoom', String(this.zoom));
    this.wrapper.nativeElement.style.setProperty('-moz-transform', `scale(${this.zoom})`);
    this.wrapper.nativeElement.style.setProperty('-moz-transform-origin', '0px 0px');
  }

  public zoomOut() {
    this.zoom -= this.zoom * 0.3;
    this.wrapper.nativeElement.style.setProperty('zoom', String(this.zoom));
    this.wrapper.nativeElement.style.setProperty('-moz-transform', `scale(${this.zoom})`);
    this.wrapper.nativeElement.style.setProperty('-moz-transform-origin', '0px 0px');
  }

  ngOnDestroy() {
    if (this.parameterSubscription) {
      this.parameterSubscription.unsubscribe();
    }
  }
}
