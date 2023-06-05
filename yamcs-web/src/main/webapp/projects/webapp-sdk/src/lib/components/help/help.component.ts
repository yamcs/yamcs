import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { DomSanitizer } from '@angular/platform-browser';
import { HelpDialog } from './help.dialog';

@Component({
  selector: 'ya-help',
  templateUrl: './help.component.html',
  styleUrls: ['./help.component.css'],
})
export class HelpComponent {

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
    this.dialog.open(HelpDialog, {
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
