import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[app-frame-host]',
})
export class FrameHost {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
