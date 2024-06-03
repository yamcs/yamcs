import { Component } from '@angular/core';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { OpiDisplayViewerComponent } from '../opi-display-viewer/opi-display-viewer.component';

@Component({
  standalone: true,
  selector: 'app-opi-display-viewer-controls',
  templateUrl: './opi-display-viewer-controls.component.html',
  imports: [
    WebappSdkModule,
  ],
})
export class OpiDisplayViewerControlsComponent {

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: OpiDisplayViewerComponent;

  public init(viewer: OpiDisplayViewerComponent) {
    this.viewer = viewer;
    this.initialized$.next(true);
  }

  fitZoom() {
    this.viewer.fitZoom();
  }

  zoomIn() {
    this.viewer.zoomIn();
  }

  zoomOut() {
    this.viewer.zoomOut();
  }

  resetZoom() {
    this.viewer.resetZoom();
  }
}
