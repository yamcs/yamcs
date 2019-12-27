import { Component, ElementRef, Input, ViewChild } from '@angular/core';

@Component({
  selector: 'app-download-button',
  templateUrl: './DownloadButton.html',
})
export class DownloadButton {

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
