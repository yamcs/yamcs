import { Component, ElementRef, Input, ViewChild } from '@angular/core';

@Component({
  selector: 'ya-download-menu-item',
  templateUrl: './download-menu-item.component.html',
})
export class DownloadMenuItemComponent {

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
