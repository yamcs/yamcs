import { Component } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { UssDisplayViewer } from './UssDisplayViewer';

@Component({
  selector: 'app-uss-display-viewer-controls',
  templateUrl: './UssDisplayViewerControls.html',
})
export class UssDisplayViewerControls {

  initialized$ = new BehaviorSubject<boolean>(false);

  viewer: UssDisplayViewer;

  public init(viewer: UssDisplayViewer) {
    this.viewer = viewer;
    this.initialized$.next(true);
  }
}
