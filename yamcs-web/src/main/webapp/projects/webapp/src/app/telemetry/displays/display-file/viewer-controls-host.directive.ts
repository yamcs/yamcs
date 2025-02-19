import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[app-viewer-controls-host]',
})
export class ViewerControlsHostDirective {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
