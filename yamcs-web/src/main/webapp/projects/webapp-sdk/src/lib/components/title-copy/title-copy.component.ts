import { Clipboard } from '@angular/cdk/clipboard';
import { AsyncPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input, ViewChild } from '@angular/core';
import { MatIcon } from '@angular/material/icon';
import { MatTooltip } from '@angular/material/tooltip';
import { BehaviorSubject } from 'rxjs';

const defaultText = "Copy to clipboard";

@Component({
  standalone: true,
  selector: 'ya-title-copy',
  templateUrl: './title-copy.component.html',
  styleUrl: './title-copy.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AsyncPipe,
    MatIcon,
    MatTooltip,
  ],
})
export class YaTitleCopy {

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
