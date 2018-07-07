import { ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { DisplayHolder, OpenDisplayCommandOptions, UssDisplay } from '@yamcs/displays';
import { YamcsService } from '../../core/services/YamcsService';
import { MyDisplayCommunicator } from './MyDisplayCommunicator';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-uss-display-viewer',
  template: `
    <div #wrapper class="wrapper">
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
export class UssDisplayViewer implements DisplayHolder, Viewer {

  @ViewChild('wrapper')
  private wrapper: ElementRef;

  @ViewChild('displayContainer')
  private displayContainer: ElementRef;

  private objectName: string;

  private zoom = 1;

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
    const display = new UssDisplay(this, container, displayCommunicator);
    display.parseAndDraw(this.objectName).then(() => {
      const ids = display!.getParameterIds();
      if (ids.length) {
        this.yamcs.getInstanceClient()!.getParameterValueUpdates({
          id: ids,
          abortOnInvalid: false,
          sendFromCache: true,
          updateOnExpiration: true,
        }).then(res => {
          res.parameterValues$.subscribe(pvals => {
            display!.processParameterValues(pvals);
          });
        });
      }
    });
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

  getBaseId() { // DisplayHolder
    return this.objectName;
  }

  openDisplay(options: OpenDisplayCommandOptions) { // DisplayHolder
    // TODO (called via e.g. NavigationButton)
  }

  closeDisplay() { // DisplayHolder
    // NOP
  }
}
