import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, OnChanges, ViewChild, inject } from '@angular/core';
import { ExtensionService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { InstancePageTemplateComponent } from '../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../shared/instance-toolbar/instance-toolbar.component';

@Component({
  standalone: true,
  templateUrl: './extension.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class ExtensionComponent implements AfterViewInit, OnChanges {

  private extensionService = inject(ExtensionService);
  private yamcs = inject(YamcsService);

  @Input()
  extension: string;

  @Input()
  subroute: string;

  @ViewChild('customElementControlsHolder')
  customElementControlsHolder: ElementRef<HTMLDivElement>;

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

    const { nativeElement: controlsHolder } = this.customElementControlsHolder;
    controlsHolder.innerHTML = `<${extension}-controls></${extension}-controls>`;

    const extensionControlsEl = controlsHolder.childNodes.item(0);
    (extensionControlsEl as any).extensionService = this.extensionService;
    (extensionControlsEl as any).mainComponent = extensionEl;
  }
}
