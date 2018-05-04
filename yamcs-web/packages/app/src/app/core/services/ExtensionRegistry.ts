import { Injectable, Type } from '@angular/core';
import { PageContent } from './PageContent';

@Injectable({
  providedIn: 'root',
})
export class ExtensionRegistry {

  private pageContentComponents: { [id: string]: Type<PageContent> } = {};

  /**
   * Registers a component for display of content within a generic
   * extension page.
   */
  registerPageContent(id: string, component: Type<PageContent>) {
    this.pageContentComponents[id] = component;
  }

  getPageContent(id: string) {
    return this.pageContentComponents[id];
  }
}
