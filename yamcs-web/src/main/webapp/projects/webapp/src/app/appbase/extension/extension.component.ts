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
    this.loadExtension(this.extension);
  }

  ngOnChanges() {
    if (this.extension && this.customElementHolder) {
      this.loadExtension(this.extension);
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
