import { Injectable, Type } from '@angular/core';
import { PageContent } from './PageContent';

@Injectable()
export class ExtensionRegistry {

  private monitorSidebarItems: Type<any>[] = [];
  private pageContentComponents: { [id: string]: Type<PageContent> } = {};

  /**
   * Registers a component for showing an additional entry
   * in the Monitor sidebar.
   */
  registerMonitorSidebarItem(component: Type<any>) {
    this.monitorSidebarItems.push(component);
  }

  /**
   * Registers a component for display of content within a generic
   * extension page.
   */
  registerPageContent(id: string, component: Type<PageContent>) {
    this.pageContentComponents[id] = component;
  }

  getMonitorSidebarItems() {
    return this.monitorSidebarItems;
  }

  getPageContent(id: string) {
    return this.pageContentComponents[id];
  }
}
