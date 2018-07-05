import { ChangeDetectionStrategy, Component } from '@angular/core';
import { YamcsService } from '../../core/services/YamcsService';
import { Viewer } from './Viewer';

@Component({
  selector: 'app-image-viewer',
  templateUrl: './ImageViewer.html',
  styles: [`
    .checkerboard {
      position: absolute;
      width: 100%;
      height: 100%;
    }
    .wrapper {
      position: absolute;
      top: 50%;
      left: 50%;
      transform: translate(-50%,-50%);
    }
  `],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ImageViewer implements Viewer {

  url: string;

  constructor(private yamcs: YamcsService) {
  }

  public loadPath(path: string) {
    const instance = this.yamcs.getInstance().name;
    this.url = `${this.yamcs.yamcsClient.staticUrl}/${instance}/displays${path}`;
  }

  public isFullscreenSupported() {
    return false;
  }
}
