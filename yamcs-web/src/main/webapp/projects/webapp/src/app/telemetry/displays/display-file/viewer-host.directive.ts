import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[app-viewer-host]',
})
export class ViewerHostDirective {
  constructor(public viewContainerRef: ViewContainerRef) {}
}
