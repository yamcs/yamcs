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

const VALID_EXTENSION_NAME_REGEX = /^[a-z][a-z0-9_]*-[a-z0-9_-]*$/;

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
    if (this.extension) {
      this.loadExtension(this.extension);
    }
  }

  ngOnChanges(changes: SimpleChanges) {
    if (!this.extension || !this.customElementHolder) {
      return;
    }

    // reloadOnNavigation is backwards compatible behavior, we'll eventually remove it.
    const reloadOnNavigation =
      !this.extensionService.isDisablingReloadOnNavigation(this.extension);

    const reloadExtension = changes['extension'] || reloadOnNavigation;

    if (this.customElementHolder && reloadExtension && this.extension) {
      this.loadExtension(this.extension);
    } else if (changes['subroute']) {
      const el = this.customElementHolder.nativeElement.firstChild as any;
      if (el) {
        el.subroute = this.subroute;
      }
    }
  }

  private loadExtension(extension: string) {
    if (!VALID_EXTENSION_NAME_REGEX.test(extension)) {
      console.error(`Blocking malformed extension name: ${extension}`);
      return;
    }

    const { nativeElement: holder } = this.customElementHolder;
    holder.innerHTML = '';
    const extensionEl = document.createElement(extension);
    (extensionEl as any).subroute = this.subroute;
    (extensionEl as any).extensionService = this.extensionService;
    holder.appendChild(extensionEl);
  }
}
