import { ChangeDetectionStrategy, Component, ElementRef, ViewChild } from '@angular/core';
import { Router } from '@angular/router';
import { DisplayHolder, OpenDisplayCommandOptions, OpiDisplay } from '@yamcs/displays';
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
export class OpiDisplayViewer implements DisplayHolder, Viewer {

  @ViewChild('displayContainer')
  private displayContainer: ElementRef;

  private path: string;

  constructor(
    private yamcs: YamcsService,
    private router: Router,
  ) { }

  /**
   * Don't call before ngAfterViewInit()
   */
  public loadPath(path: string) {
    this.path = path;

    const container: HTMLDivElement = this.displayContainer.nativeElement;
    const displayCommunicator = new MyDisplayCommunicator(this.yamcs, this.router);
    const display = new OpiDisplay(this, container, displayCommunicator);
    display.parseAndDraw(this.path).then(() => {
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

  getBaseId() { // DisplayHolder
    return this.path;
  }

  openDisplay(options: OpenDisplayCommandOptions) { // DisplayHolder
    // TODO (called via e.g. NavigationButton)
  }

  closeDisplay() { // DisplayHolder
    // NOP
  }
}
