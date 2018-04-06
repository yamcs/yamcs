import { Directive, ViewContainerRef } from '@angular/core';

@Directive({
  selector: '[monitor-sidebar-item-host]',
})
export class MonitorSidebarItemHost {

  constructor(public viewContainerRef: ViewContainerRef) {
  }
}
