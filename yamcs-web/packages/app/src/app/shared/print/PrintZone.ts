import { ChangeDetectionStrategy, Component, ComponentFactory, ElementRef, ViewChild } from '@angular/core';
import { PrintService } from '../../core/services/PrintService';
import { Printable } from './Printable';
import { PrintableDirective } from './PrintableDirective';

@Component({
  selector: 'app-print-zone',
  templateUrl: './PrintZone.html',
  styleUrls: ['./PrintZone.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrintZone {

  @ViewChild('wrapper', { static: true })
  private printableContent: ElementRef;

  @ViewChild(PrintableDirective, { static: true })
  private printableHost: PrintableDirective;

  constructor(private printService: PrintService) {
    this.printService.printOrders$.subscribe(order => {
      this.createAndPrint(order.factory, order.title, order.data);
    });
  }

  private createAndPrint(factory: ComponentFactory<Printable>, pageTitle: string, data: any) {
    const viewContainerRef = this.printableHost.viewContainerRef;
    viewContainerRef.clear();
    const componentRef = viewContainerRef.createComponent(factory);
    (<Printable>componentRef.instance).pageTitle = pageTitle;
    (<Printable>componentRef.instance).data = data;

    // Realise content
    componentRef.changeDetectorRef.detectChanges();

    const iframeEl = document.createElement('iframe') as HTMLIFrameElement;
    iframeEl.style.display = 'none';
    document.body.appendChild(iframeEl);
    const iframeDoc = iframeEl.contentDocument!;
    iframeDoc.title = pageTitle;

    // Import styles from parent frame
    const styleEls = document.getElementsByTagName('style');
    const iframeHeadEl = iframeDoc.getElementsByTagName('head')[0];
    for (let i = 0; i < styleEls.length; i++) {
      iframeHeadEl.appendChild(styleEls[i].cloneNode(true));
    }

    const printableEl = this.printableContent.nativeElement as HTMLDivElement;
    iframeDoc.body.appendChild(printableEl.cloneNode(true));
    iframeEl.focus();
    iframeEl.contentWindow!.print();
    document.body.removeChild(iframeEl);
  }
}
