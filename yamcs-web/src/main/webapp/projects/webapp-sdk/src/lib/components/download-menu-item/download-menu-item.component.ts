import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { MatMenuItem } from '@angular/material/menu';

@Component({
  standalone: true,
  selector: 'ya-download-menu-item',
  templateUrl: './download-menu-item.component.html',
  imports: [
    MatMenuItem,
  ],
})
export class YaDownloadMenuItem {

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
