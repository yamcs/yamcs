import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  standalone: true,
  selector: '[app-viewer-host]',
})
export class ViewerHostDirective {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
