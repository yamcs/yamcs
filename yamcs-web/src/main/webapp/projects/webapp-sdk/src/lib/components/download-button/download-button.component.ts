import { booleanAttribute, Component, ElementRef, input, ViewChild } from '@angular/core';
import { YaButton, YaButtonAppearance } from '../button/button.component';

@Component({
  standalone: true,
  selector: 'ya-download-button',
  templateUrl: './download-button.component.html',
  imports: [
    YaButton,
  ],
})
export class YaDownloadButton {

  link = input.required<string>();
  disabled = input(false, { transform: booleanAttribute });
  appearance = input<YaButtonAppearance>('basic');

  @ViewChild('hiddenLink', { static: true })
  private hiddenLink: ElementRef;

  triggerDownload() {
    if (!this.disabled()) {
      this.hiddenLink.nativeElement.click();
    }
  }
}
