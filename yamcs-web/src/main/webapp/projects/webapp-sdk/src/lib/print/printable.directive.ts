import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  standalone: true,
  selector: '[printable-host]',
})
export class PrintableDirective {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
