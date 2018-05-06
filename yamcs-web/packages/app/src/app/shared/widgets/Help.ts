import { Component, ElementRef, Input, ViewChild } from '@angular/core';
import { MatDialog } from '@angular/material';
import { DomSanitizer } from '@angular/platform-browser';
import { HelpDialog } from '../dialogs/HelpDialog';

@Component({
  selector: 'app-help',
  templateUrl: './Help.html',
})
export class Help {

  @Input()
  dialogTitle: string;

  @ViewChild('dialogContent')
  dialogContent: ElementRef;

  constructor(private dialog: MatDialog, private sanitizer: DomSanitizer) {
  }

  showHelp() {
    const html = this.dialogContent.nativeElement.innerHTML;
    this.dialog.open(HelpDialog, {
      width: '500px',
      data: {
        title: this.dialogTitle,
        content: this.sanitizer.bypassSecurityTrustHtml(html),
      }
    });
  }
}
