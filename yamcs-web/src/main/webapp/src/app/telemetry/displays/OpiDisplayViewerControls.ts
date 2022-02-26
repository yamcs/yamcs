import { Component } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { OpiDisplayViewer } from './OpiDisplayViewer';

@Component({
  selector: 'app-opi-display-viewer-controls',
  templateUrl: './OpiDisplayViewerControls.html',
})
export class OpiDisplayViewerControls {

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: OpiDisplayViewer;

  public init(viewer: OpiDisplayViewer) {
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
