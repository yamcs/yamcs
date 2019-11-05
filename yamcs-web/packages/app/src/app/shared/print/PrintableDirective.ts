import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[printable-host]',
})
export class PrintableDirective {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
