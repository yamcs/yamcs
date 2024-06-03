import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, EventEmitter, HostListener, Output, ViewChild } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { CommandLine } from './CommandLine';
import { ScrollBuffer } from './ScrollBuffer';

@Component({
  standalone: true,
  selector: 'app-shell',
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class ShellComponent implements AfterViewInit {

  @ViewChild('shell')
  private shellEl: ElementRef;

  @ViewChild('bottom')
  private bottomAnchor: ElementRef;

  private scrollBuffer = new ScrollBuffer(500);
  lines$ = new BehaviorSubject<string[]>([]);

  ready$ = new BehaviorSubject<boolean>(false);

  prompt$ = new BehaviorSubject<string>('');
  focused$ = new BehaviorSubject<boolean>(false);

  private commandLine: CommandLine;
  commandLineHTML$ = new BehaviorSubject<SafeHtml>('');

  @Output()
  command = new EventEmitter<string>();

  constructor(private sanitizer: DomSanitizer) {
    this.commandLine = new CommandLine(this);
    this.repaintCommandLine();
  }

  ngAfterViewInit() {
    (this.shellEl.nativeElement as HTMLElement).focus();
  }

  onFocus() {
    this.focused$.next(true);
  }

  onFocusOut() {
    this.focused$.next(false);
  }

  onPaste(event: ClipboardEvent) {
    const text = event.clipboardData?.getData('Text');
    if (text) {
      this.commandLine.consume(text);
    }
    event.preventDefault();
  }

  public printLines(lines: string[]) {
    for (const line of lines) {
      this.scrollBuffer.add(line);
    }
    this.lines$.next(this.scrollBuffer.snapshot());
    this.scrollToBottom();
  }

  public printLine(line = '') {
    this.scrollBuffer.add(line);
    this.lines$.next(this.scrollBuffer.snapshot());
    this.scrollToBottom();
  }

  setPrompt(prompt: string) {
    this.prompt$.next(prompt);
  }

  @HostListener('document:keydown', ['$event'])
  handleKeyDownEvent(event: KeyboardEvent) {
    if (event.ctrlKey) {
      switch (event.key) {
        case 'a': // Go to beginning of line
          this.commandLine.moveCursorToHome();
          event.preventDefault();
          break;
        case 'c': // Abort
          const input = this.commandLine.abort();
          this.printLine(this.prompt$.value + input);
          event.preventDefault();
          break;
        case 'e': // End of line
          this.commandLine.moveCursorToEnd();
          event.preventDefault();
          break;
        case 'h': // Backspace
          this.commandLine.consume('Backspace');
          event.preventDefault();
          break;
        case 'l': // Clear screen
          this.scrollBuffer.reset();
          this.lines$.next(this.scrollBuffer.snapshot());
          event.preventDefault();
          break;
      }
    } else if (!event.shiftKey && !event.metaKey) {
      switch (event.key) {
        case 'ArrowUp':
        case 'ArrowDown':
        case 'ArrowLeft':
        case 'ArrowRight':
        case 'Backspace':
          this.commandLine.consume(event.key);
          event.preventDefault();
          break;
      }
    }
  }

  @HostListener('document:keypress', ['$event'])
  handleKeyboardEvent(event: KeyboardEvent) {
    if (!this.focused$.value) {
      return;
    }
    if (event.ctrlKey || event.altKey || event.metaKey) {
      return;
    }

    switch (event.key) {
      case 'Enter':
        const input = this.commandLine.take();
        this.printLine(this.prompt$.value + input);
        this.command.next(input.trim());
        event.preventDefault();
        break;
      default:
        if (event.key.length === 1) {
          this.scrollToBottom();
          this.commandLine.consume(event.key);
          event.preventDefault();
        }
    }
  }

  repaintCommandLine() {
    let html = '';
    let cursorMatch = false;
    const line = this.commandLine.peek();
    const cursor = this.commandLine.cursor;
    for (let i = 0; i < line.length; i++) {
      if (cursor === i) {
        html += '<span class="cursor">' + line[i] + '</span>';
        cursorMatch = true;
      } else {
        html += line[i];
      }
    }
    if (!cursorMatch) {
      html += '<span class="cursor"> </span>';
    }
    this.commandLineHTML$.next(this.sanitizer.bypassSecurityTrustHtml(html));
  }

  private scrollToBottom() {
    window.setTimeout(() => { // T/O so that page has a chance to update first
      this.bottomAnchor.nativeElement.scrollIntoView();
    });
  }
}
