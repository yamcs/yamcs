import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[page-content-host]',
})
export class PageContentHost {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
