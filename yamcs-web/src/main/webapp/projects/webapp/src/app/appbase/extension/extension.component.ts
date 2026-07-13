import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Input,
  OnChanges,
  ViewChild,
  inject,
} from '@angular/core';
import { ExtensionService, WebappSdkModule } from '@yamcs/webapp-sdk';

const VALID_EXTENSION_NAME_REGEX = /^[a-z][a-z0-9_]*-[a-z0-9_-]*$/;

@Component({
  templateUrl: './extension.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [WebappSdkModule],
})
export class ExtensionComponent implements AfterViewInit, OnChanges {
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

  ngOnChanges() {
    if (this.customElementHolder && this.extension) {
      this.loadExtension(this.extension);
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
