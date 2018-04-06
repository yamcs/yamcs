import { Component, ChangeDetectionStrategy, OnInit, ComponentFactoryResolver, ViewChild } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { ExtensionRegistry } from '../../core/services/ExtensionRegistry';
import { PageContentHost } from './PageContentHost';
import { Title } from '@angular/platform-browser';

@Component({
  templateUrl: './ExtensionPage.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExtensionPage implements OnInit {

  @ViewChild(PageContentHost)
  extensionHost: PageContentHost;

  title: string;

  constructor(
    private route: ActivatedRoute,
    private extensionRegistry: ExtensionRegistry,
    private componentFactoryResolver: ComponentFactoryResolver,
    private titleService: Title,
  ) {}

  ngOnInit() {
    const extensionName = this.route.snapshot.paramMap.get('name')!;
    const extensionType = this.extensionRegistry.getPageContent(extensionName);
    if (extensionType) {
      const componentFactory = this.componentFactoryResolver.resolveComponentFactory(extensionType);
      const viewContainerRef = this.extensionHost.viewContainerRef;
      const pageContentRef = viewContainerRef.createComponent(componentFactory);
      this.title = pageContentRef.instance.getTitle();
      this.titleService.setTitle(this.title);
    } else {
      console.warn(`Failed to load extension ${extensionName}`);
    }
  }
}
