import { Clipboard } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatLegacyTooltip } from '@angular/material/legacy-tooltip';
import { BehaviorSubject } from 'rxjs';

const defaultText = "Copy to clipboard";

@Component({
  selector: 'app-title-copy',
  templateUrl: './TitleCopy.html',
  styleUrls: ['./TitleCopy.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TitleCopy {

  @Input()
  text: string;

  @ViewChild('tooltip')
  tooltip: MatLegacyTooltip;

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
