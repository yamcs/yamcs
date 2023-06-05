import { Component, ElementRef, Input, ViewChild } from '@angular/core';

@Component({
  selector: 'ya-download-button',
  templateUrl: './download-button.component.html',
})
export class DownloadButtonComponent {

  @Input()
  link: string;

  @Input()
  disabled = false;

  @Input()
  primary = false;

  @ViewChild('hiddenLink', { static: true })
  private hiddenLink: ElementRef;

  triggerDownload() {
    if (!this.disabled) {
      this.hiddenLink.nativeElement.click();
    }
  }
}
