import { Component, ElementRef, Input, ViewChild } from '@angular/core';

@Component({
  selector: 'app-download-menu-item',
  templateUrl: './DownloadMenuItem.html',
})
export class DownloadMenuItem {

  @Input()
  link: string;

  @Input()
  disabled = false;

  @ViewChild('hiddenLink', { static: true })
  private hiddenLink: ElementRef;

  triggerDownload() {
    if (!this.disabled) {
      this.hiddenLink.nativeElement.click();
    }
  }
}
