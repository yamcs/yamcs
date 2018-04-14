import { Component, ChangeDetectionStrategy, OnInit, ViewChild, ComponentFactoryResolver } from '@angular/core';
import { Instance } from '@yamcs/client';
import { YamcsService } from '../../core/services/YamcsService';
import { ExtensionRegistry } from '../../core/services/ExtensionRegistry';
import { MonitorSidebarItemHost } from '../ext/MonitorSidebarItemHost';
import { AuthService } from '../../core/services/AuthService';

@Component({
  templateUrl: './MonitorPage.html',
  styleUrls: ['./MonitorPage.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MonitorPage implements OnInit {

  @ViewChild(MonitorSidebarItemHost)
  extensionHost: MonitorSidebarItemHost;

  instance: Instance;

  constructor(
    yamcs: YamcsService,
    private authService: AuthService,
    private extensionRegistry: ExtensionRegistry,
    private componentFactoryResolver: ComponentFactoryResolver,
  ) {
    this.instance = yamcs.getInstance();
  }

  ngOnInit() {
    // Add extra items from extensions.
    for (const item of this.extensionRegistry.getMonitorSidebarItems()) {
      const componentFactory = this.componentFactoryResolver.resolveComponentFactory(item);
      const viewContainerRef = this.extensionHost.viewContainerRef;
      viewContainerRef.createComponent(componentFactory);
    }
  }

  showEventsItem() {
    return this.authService.hasSystemPrivilege('MayReadEvents');
  }
}
