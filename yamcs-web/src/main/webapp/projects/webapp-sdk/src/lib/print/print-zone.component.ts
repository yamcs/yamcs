import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Type,
  ViewChild,
} from '@angular/core';
import { Printable } from './Printable';
import { PrintService } from './print.service';
import { PrintableDirective } from './printable.directive';

@Component({
  selector: 'ya-print-zone',
  templateUrl: './print-zone.component.html',
  styleUrl: './print-zone.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgTemplateOutlet, PrintableDirective],
})
export class YaPrintZone {
  /*
   * Implementation note:
   *
   * Test in Chrome, FF and Safari before committing
   * changes. Very tricky.
   *
   * Probably printing from a new tab instead of an iframe
   * would be easier.
   *
   * Currently still one bug in Safari when doing:
   * print -> cancel print -> print.
   */

  @ViewChild('wrapper', { static: true })
  private printableContent: ElementRef;

  @ViewChild(PrintableDirective, { static: true })
  private printableHost: PrintableDirective;

  constructor(private printService: PrintService) {
    this.printService.printOrders$.subscribe((order) => {
      this.createAndPrint(order.componentType, order.title, order.data);
    });
  }

  private createAndPrint(
    componentType: Type<Printable>,
    pageTitle: string,
    data: any,
  ) {
    const viewContainerRef = this.printableHost.viewContainerRef;
    viewContainerRef.clear();
    const componentRef = viewContainerRef.createComponent(componentType);
    (<Printable>componentRef.instance).pageTitle = pageTitle;
    (<Printable>componentRef.instance).data = data;

    // Realise content
    componentRef.changeDetectorRef.detectChanges();

    const prevFrames = document.getElementsByClassName('printable');
    for (let i = 0; i < prevFrames.length; i++) {
      document.body.removeChild(prevFrames[i]);
    }

    const iframeEl = document.createElement('iframe') as HTMLIFrameElement;
    iframeEl.className = 'printable';
    iframeEl.style.display = 'none';
    document.body.appendChild(iframeEl);
    const iframeDoc = iframeEl.contentDocument!;
    iframeDoc.title = pageTitle;

    iframeDoc.open();
    iframeDoc.writeln('<!doctype html>');
    iframeDoc.writeln('<head>');
    iframeDoc.writeln(`
      <style>
      body {
        font: 400 12px / 14px Roboto, sans-serif;
      }
      .ya-attr-label {
        margin-top: 1em;
        font-weight: bold;
      }
      .no-print, .no-print * {
        display: none !important;
      }
      </style>
    `);
    iframeDoc.writeln('</head>');

    iframeDoc.writeln('<body onload="window.print()">');
    const printableEl = this.printableContent.nativeElement as HTMLDivElement;
    iframeDoc.writeln(printableEl.innerHTML);
    iframeDoc.writeln('</body>');
    iframeDoc.writeln('</html>');
    iframeDoc.close();
  }
}
