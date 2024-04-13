import { Component, ElementRef, Input, ViewChild } from '@angular/core';

@Component({
  standalone: true,
  selector: 'ya-download-button',
  templateUrl: './download-button.component.html',
})
export class YaDownloadButton {

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
