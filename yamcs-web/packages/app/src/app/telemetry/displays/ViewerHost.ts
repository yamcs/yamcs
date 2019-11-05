import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[app-viewer-host]',
})
export class ViewerHost {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
