import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatTooltip } from '@angular/material/tooltip';
import { BehaviorSubject } from 'rxjs';

const defaultText = "Copy to clipboard";

@Component({
  selector: 'ya-title-copy',
  templateUrl: './title-copy.component.html',
  styleUrls: ['./title-copy.component.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TitleCopyComponent {

  @Input()
  text: string;

  @ViewChild('tooltip')
  tooltip: MatTooltip;

  tooltip$ = new BehaviorSubject<string>(defaultText);

  constructor(private clipboard: Clipboard) {
  }

  doCopy() {
    if (this.clipboard.copy(this.text)) {
      this.tooltip$.next("Copied!");
    } else {
      this.tooltip$.next("Copy failed!");
    }
    this.tooltip.show();
    setTimeout(() => {
      this.tooltip.hide();
      this.tooltip$.next(defaultText);
    }, 1500);
  }
}
