import { ChangeDetectionStrategy, ChangeDetectorRef, Component } from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-text-viewer',
  template: `
    <pre *ngIf="text">{{ text }}</pre>
  `,
  styles: [`
    pre {
      margin: 1em;
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TextViewer implements Viewer {

  text: string;

  constructor(private yamcs: YamcsService, private changeDetector: ChangeDetectorRef) {
  }

  public loadPath(path: string) {
    const instance = this.yamcs.getInstance().name;
    this.yamcs.yamcsClient.getStaticText(`${instance}/displays${path}`).then(text => {
      this.text = text;
      this.changeDetector.detectChanges();
    });
  }

  public isFullscreenSupported() {
    return false;
  }
}
