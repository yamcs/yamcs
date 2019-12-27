import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[app-viewer-controls-host]',
})
export class ViewerControlsHost {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
