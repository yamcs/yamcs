import { ChangeDetectionStrategy, Component, ElementRef, HostListener, Input, OnChanges, OnDestroy } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { BehaviorSubject } from 'rxjs';
import { BitRange } from '../BitRange';
import { HexModel } from './model';

@Component({
  selector: 'app-hex',
  templateUrl: './Hex.html',
  styleUrls: ['./Hex.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Hex implements OnChanges, OnDestroy {

  @Input()
  base64String: string;

  @Input()
  fontSize = 10;

  model$ = new BehaviorSubject<HexModel | null>(null);
  html$ = new BehaviorSubject<SafeHtml | null>(null);

  private lastSelection: BitRange;
  private lastHighlight: BitRange;

  // Bit position when a press was started
  private pressStart?: BitRange;

  private loadWorker: Worker;

  constructor(private ref: ElementRef, sanitizer: DomSanitizer) {
    this.loadWorker = new Worker('./worker', { type: 'module' });
    this.loadWorker.onmessage = ({ data }) => {
      switch (data.type) {
        case 'model':
          const model = data.model;
          this.clearHighlight();
          this.clearSelection();
          this.model$.next(model);
          this.html$.next(sanitizer.bypassSecurityTrustHtml(data.html));
          break;
        case 'selection':
          this.clearSelection();
          for (const id of data.selection) {
            document.getElementById(id)?.classList.add('selected');
          }
          break;
        case 'highlight':
          this.clearHighlight();
          for (const id of data.ids) {
            document.getElementById(id)?.classList.add('hl');
          }
          break;
        default:
          throw Error(`Unexpected worker response type ${data.type}`);
      }
    };
  }

  ngOnChanges() {
    this.loadWorker.postMessage({
      type: 'init',
      options: {
        base64String: this.base64String,
      }
    });
  }

  @HostListener('mousedown', ['$event'])
  onMouseDown(event: MouseEvent) {
    const el = event.target as HTMLElement;
    const model = this.model$.value!;

    if (el.classList.contains('cnt')) {
      const lineEl = el.parentElement!;
      const bitpos = model.positionables.get(lineEl.id)!;
      const lineRange = new BitRange(bitpos, 128);
      this.pressStart = lineRange;
      this.setSelection(lineRange);
    } else if (el.classList.contains('nibble')) {
      const wordEl = el.parentElement!;
      const bitpos = model.positionables.get(wordEl.id)!;
      const wordRange = new BitRange(bitpos, 16);
      this.pressStart = wordRange;
      this.setSelection(wordRange);
    } else if (el.classList.contains('filler')) {
      const bitpos = model.positionables.get(el.id)!;
      this.pressStart = new BitRange(bitpos, 0);
    } else if (el.classList.contains('char')) {
      const bitpos = model.positionables.get(el.id)!;
      const charRange = new BitRange(bitpos, 8);
      this.pressStart = charRange;
      this.setSelection(charRange);
    }

    // Prevent text selection when mouse leaves our component
    event.preventDefault();
    return false;
  }

  @HostListener('mouseover', ['$event.target', '!!$event.which'])
  onMouseOver(el: HTMLElement, pressing: boolean) {
    const model = this.model$.value!;

    if (el.classList.contains('cnt')) {
      // Highlight entire line if the leading charcount is hovered
      const lineEl = el.parentElement!;
      const bitpos = model.positionables.get(lineEl.id)!;
      const lineRange = new BitRange(bitpos, 128);

      if (pressing && this.pressStart) {
        const joined = this.pressStart.join(lineRange);
        this.setSelection(joined);
      } else {
        this.setHighlight(lineRange);
      }
    } else if (el.classList.contains('nibble')) {
      // Highlight entire word when any of its four nibbles is hovered
      const wordEl = el.parentElement!;
      const bitpos = model.positionables.get(wordEl.id)!;
      const wordRange = new BitRange(bitpos, 16);

      if (pressing && this.pressStart) {
        // Select all bits between current word and word when mouse was pressed
        const joined = this.pressStart.join(wordRange);
        this.setSelection(joined);
      } else {
        this.setHighlight(wordRange);
      }
    } else if (el.classList.contains('filler')) {
      const fillerpos = model.positionables.get(el.id)!;
      if (pressing && this.pressStart) {
        const joined = this.pressStart.joinBit(fillerpos);
        this.setSelection(joined);
      }
    } else if (el.classList.contains('char')) {
      // Highlight byte when a character is hovered
      const bitpos = model.positionables.get(el.id)!;
      const charRange = new BitRange(bitpos, 8);

      if (pressing && this.pressStart) {
        const joined = this.pressStart.join(charRange);
        this.setSelection(joined);
      } else {
        this.setHighlight(charRange);
      }
    }
  }

  @HostListener('document:mouseup')
  onMouseUp() {
    this.pressStart = undefined;
  }

  @HostListener('document:mouseout')
  onMouseOut() {
    // Handle via worker, just in case an operation is already underway.
    this.setHighlight(new BitRange(0, 0));
  }

  private clearSelection() {
    (this.ref.nativeElement as HTMLElement).querySelectorAll('.selected').forEach(el => {
      el.classList.remove('selected');
    });
  }

  private clearHighlight() {
    (this.ref.nativeElement as HTMLElement).querySelectorAll('.hl').forEach(el => {
      el.classList.remove('hl');
    });
  }

  private setSelection(range: BitRange) {
    // Avoid spamming the worker thread
    if (this.lastSelection && this.lastSelection.equals(range)) {
      return;
    }

    this.lastSelection = range;
    this.loadWorker.postMessage({
      type: 'select',
      options: {
        bitpos: range.start,
        bitlength: range.bitlength,
      }
    });
  }

  private setHighlight(range: BitRange) {
    // Avoid spamming the worker thread
    if (this.lastHighlight && this.lastHighlight.equals(range)) {
      return;
    }

    this.lastHighlight = range;
    this.loadWorker.postMessage({
      type: 'highlight',
      options: {
        bitpos: range.start,
        bitlength: range.bitlength,
      }
    });
  }

  ngOnDestroy() {
    if (this.loadWorker) {
      this.loadWorker.terminate();
    }
  }
}
