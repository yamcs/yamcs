import { ChangeDetectionStrategy, Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';

@Component({
  selector: 'app-printable-content',
  templateUrl: './PrintableContent.html',
  styleUrls: ['./PrintableContent.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrintableContent implements OnDestroy {

  @Input()
  private headerTitle: string;

  @ViewChild('printableContent', { static: true })
  private printableContent: ElementRef;

  private iframeEl: HTMLIFrameElement;

  print() {
    if (this.iframeEl) {
      document.body.removeChild(this.iframeEl);
    }

    const iframeEl = document.createElement('iframe');
    iframeEl.style.display = 'none';
    document.body.appendChild(iframeEl);
    const iframeDoc = iframeEl.contentDocument!;
    if (this.headerTitle) {
      iframeDoc.title = this.headerTitle;
    }

    const styleEls = document.getElementsByTagName('style');
    const iframeHeadEl = iframeDoc.getElementsByTagName('head')[0];
    for (let i = 0; i < styleEls.length; i++) {
      iframeHeadEl.appendChild(styleEls[i].cloneNode(true));
    }

    const printableEl = this.printableContent.nativeElement as HTMLDivElement;
    iframeDoc.body.appendChild(printableEl.cloneNode(true));
    iframeEl.focus();
    iframeEl.contentWindow!.print();
  }

  ngOnDestroy() {
    if (this.iframeEl) {
      document.body.removeChild(this.iframeEl);
    }
  }
}
