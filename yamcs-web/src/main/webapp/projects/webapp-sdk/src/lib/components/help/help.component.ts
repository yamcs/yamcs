import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MatIcon } from '@angular/material/icon';
import { DomSanitizer } from '@angular/platform-browser';
import { YaTextAction } from '../text-action/text-action.component';
import { YaHelpDialog } from './help.dialog';

@Component({
  standalone: true,
  selector: 'ya-help',
  templateUrl: './help.component.html',
  styleUrl: './help.component.css',
  imports: [
    MatIcon,
    YaTextAction,
  ],
})
export class YaHelp {

  @Input()
  dialogTitle: string;

  @Input()
  dialogWidth = '500px';

  @ViewChild('dialogContent', { static: true })
  dialogContent: ElementRef;

  constructor(private dialog: MatDialog, private sanitizer: DomSanitizer) {
  }

  showHelp() {
    const html = this.dialogContent.nativeElement.innerHTML;
    this.dialog.open(YaHelpDialog, {
      width: this.dialogWidth,
      data: {
        title: this.dialogTitle,
        content: this.sanitizer.bypassSecurityTrustHtml(html),
      }
    });

    // Prevent further click handling.
    // (for example because this component was used in a <label/>)
    return false;
  }
}
