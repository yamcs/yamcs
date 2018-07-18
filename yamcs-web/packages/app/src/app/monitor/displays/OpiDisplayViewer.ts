import { ChangeDetectionStrategy, Component, ElementRef, OnDestroy, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { NavigationHandler, OpenDisplayCommandOptions, OpiDisplay } from '@yamcs/displays';
import { Subscription } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from './MyDisplayCommunicator';
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
      top: 50%;
      left: 50%;
      transform: translate(-50%,-50%);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OpiDisplayViewer implements NavigationHandler, Viewer, OnDestroy {

  @ViewChild('displayContainer')
  private displayContainer: ElementRef;

  private objectName: string;

  display: OpiDisplay;

  private parameterSubscription: Subscription;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
  ) { }

  /**
   * Don't call before ngAfterViewInit()
   */
  public init(objectName: string) {
    this.objectName = objectName;

    const container: HTMLDivElement = this.displayContainer.nativeElement;
    const displayCommunicator = new MyDisplayCommunicator(this.yamcs, this.router);
    this.display = new OpiDisplay(this, container, displayCommunicator);
    return this.display.parseAndDraw(objectName).then(() => {
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

  public isFullscreenSupported() {
    return true;
  }

  public isScaleSupported() {
    return true;
  }

  public hasPendingChanges() {
    return false;
  }

  getBaseId() { // DisplayHolder
    return this.objectName;
  }

  openDisplay(options: OpenDisplayCommandOptions) { // DisplayHolder
    // TODO (called via e.g. NavigationButton)
  }

  closeDisplay() { // DisplayHolder
    // NOP
  }

  ngOnDestroy() {
    if (this.parameterSubscription) {
      this.parameterSubscription.unsubscribe();
    }
  }
}
