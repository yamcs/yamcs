import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { DefaultNavigationHandler } from './DefaultNavigationHandler';
import { MyDisplayCommunicator } from './MyDisplayCommunicator';
import { NavigationHandler } from './NavigationHandler';
import { OpiDisplay } from './opi/OpiDisplay';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-opi-display-viewer',
  template: `
    <div class="wrapper">
      <div #displayContainer style="line-height: 0"></div>
    </div>
  `,
  styles: [`
    .wrapper {
      position: absolute;
      top: 0%;
      left: 50%;
      transform: translate(-50%,0%);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpiDisplayViewer implements Viewer, OnDestroy {

  @ViewChild('displayContainer', { static: true })
  private displayContainer: ElementRef;

  private objectName: string;

  display: OpiDisplay;

  private parameterSubscription: Subscription;

  private navigationHandler: NavigationHandler;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
  ) { }

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
    this.display = new OpiDisplay(objectName, this.navigationHandler, container, displayCommunicator);
    return this.display.parseAndDraw(objectName).then(() => {
      const ids = this.display.getParameterIds();
      if (ids.length) {
        this.yamcs.getInstanceClient()!.getParameterValueUpdates({
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
          useNumericIds: true,
        }).then(res => {
          this.parameterSubscription = res.parameterValues$.subscribe(pvals => {
            for (const pval of pvals) {
              pval.id = res.mapping[pval.numericId];
            }
            this.display.processParameterValues(pvals);
          });
        });
      }
    });
  }

  public isFullscreenSupported() {
    return true;
  }

  public isScaleSupported() {
    return true;
  }

  public hasPendingChanges() {
    return false;
  }

  ngOnDestroy() {
    if (this.parameterSubscription) {
      this.parameterSubscription.unsubscribe();
    }
  }
}
