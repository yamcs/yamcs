import {
  AfterViewInit,
  Component,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
  ViewChild,
  inject,
} from '@angular/core';
import {
  ConfigService,
  ExtensionService,
  WebappSdkModule,
} from '@yamcs/webapp-sdk';

@Component({
  templateUrl: './extension.component.html',
  imports: [WebappSdkModule],
})
export class ExtensionComponent implements AfterViewInit, OnChanges {
  private configService = inject(ConfigService);
  private extensionService = inject(ExtensionService);

  @Input()
  extension: string;

  @Input()
  subroute: string;

  @ViewChild('customElementHolder')
  customElementHolder: ElementRef<HTMLDivElement>;

  ngAfterViewInit() {
    this.loadExtension(this.extension);
  }

  ngOnChanges(changes: SimpleChanges) {
    if (!this.extension || !this.customElementHolder) {
      return;
    }

    // reloadOnNavigation is backwards compatible behavior, we'll eventually remove it.
    const reloadOnNavigation =
      !this.extensionService.isDisablingReloadOnNavigation(this.extension);
    if (changes['extension'] || reloadOnNavigation) {
      this.loadExtension(this.extension);
    } else if (changes['subroute']) {
      const el = this.customElementHolder.nativeElement.firstChild as any;
      if (el) {
        el.subroute = this.subroute;
      }
    }
  }

  private loadExtension(extension: string) {
    const { nativeElement: holder } = this.customElementHolder;
    holder.innerHTML = `<${extension}></${extension}>`;

    const extensionEl = holder.childNodes.item(0);
    (extensionEl as any).subroute = this.subroute;
    (extensionEl as any).extensionService = this.extensionService;
  }
}
