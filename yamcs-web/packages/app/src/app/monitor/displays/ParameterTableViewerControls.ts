import { Component } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';
import { ModelListener, ParameterTableViewer } from './ParameterTableViewer';

@Component({
  selector: 'app-parameter-table-viewer-controls',
  templateUrl: './ParameterTableViewerControls.html',
})
export class ParameterTableViewerControls implements ModelListener {

  initialized$ = new BehaviorSubject<boolean>(false);

  /**
   * Whether there are changes to save.
   */
  dirty$ = new BehaviorSubject<boolean>(false);

  /**
   * Whether new values are blocked from showing.
   */
  paused$: Observable<boolean>;

  private viewer: ParameterTableViewer;

  constructor(private yamcs: YamcsService) {
  }

  public init(viewer: ParameterTableViewer) {
    viewer.modelListener = this;
    this.viewer = viewer;
    this.paused$ = viewer.paused$;
    this.initialized$.next(true);
  }

  onModelChange() {
    this.dirty$.next(true);
    console.log('model is now ', this.viewer.model);
  }

  save() {
    this.dirty$.next(false);

    const path = this.viewer.path;
    const model = this.viewer.model;
    this.yamcs.getInstanceClient()!.saveDisplay(path, model);
  }

  pause() {
    this.viewer.pause();
  }

  unpause() {
    this.viewer.unpause();
  }
}
