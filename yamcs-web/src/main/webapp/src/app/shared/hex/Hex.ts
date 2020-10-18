import { ChangeDetectionStrategy, Component, ElementRef, HostListener, Input, OnChanges, QueryList, ViewChildren } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { BitRange, HexModel } from './model';

@Component({
  selector: 'app-hex',
  templateUrl: './Hex.html',
  styleUrls: ['./Hex.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Hex implements OnChanges {

  @Input()
  base64String: string;

  @Input()
  fontSize = 10;

  @ViewChildren('nibble')
  private nibbleRefs: QueryList<ElementRef>;

  @ViewChildren('hexFiller')
  private hexFillerRefs: QueryList<ElementRef>;

  @ViewChildren('char')
  private charRefs: QueryList<ElementRef>;

  model$ = new BehaviorSubject<HexModel | null>(null);
  private highlightedEls: HTMLElement[] = [];
  private selectedEls: HTMLElement[] = [];

  // Bit position when a press was started
  private linePressStart?: BitRange;
  private hexPressStart?: BitRange;
  private hexFillerPressStart?: number;
  private asciiPressStart?: BitRange;
  private asciiFillerPressStart?: number;

  ngOnChanges() {
    this.clearHighlight();
    this.clearSelection();

    const raw = atob(this.base64String);
    this.model$.next(new HexModel(raw));
  }

  @HostListener('mousedown', ['$event'])
  onMouseDown(event: MouseEvent) {
    this.clearSelection();
    const el = event.target as HTMLElement;

    if (el.classList.contains('cnt')) {
      const line = el.parentElement!;
      const lineRange = new BitRange(Number(line.dataset.bitpos), 128);
      this.linePressStart = lineRange;
      this.setSelection(lineRange);
    } else if (el.classList.contains('nibble')) {
      const word = el.parentElement!;
      const wordRange = new BitRange(Number(word.dataset.bitpos), 16);
      this.hexPressStart = wordRange;
      this.setSelection(wordRange);
    } else if (el.classList.contains('hexfiller')) {
      this.hexFillerPressStart = Number(el.dataset.bitpos);
    } else if (el.classList.contains('char')) {
      const charRange = new BitRange(Number(el.dataset.bitpos), 8);
      this.asciiPressStart = charRange;
      this.setSelection(charRange);
    } else if (el.classList.contains('asciifiller')) {
      this.asciiFillerPressStart = Number(el.dataset.bitpos);
    }

    // Prevent text selection when mouse leaves our component
    event.preventDefault();
    return false;
  }

  @HostListener('mouseover', ['$event.target', '!!$event.which'])
  onMouseOver(el: HTMLElement, pressing: boolean) {
    this.clearHighlight();

    if (el.classList.contains('cnt')) {
      // Highlight entire line if the leading charcount is hovered
      const lineEl = el.parentElement!;
      const lineRange = new BitRange(Number(lineEl.dataset.bitpos), 128);
      this.addToHighlight(lineRange);

      if (pressing && this.linePressStart) {
        const joined = this.linePressStart.join(lineRange);
        this.setSelection(joined);
      }
    } else if (el.classList.contains('nibble')) {
      // Highlight entire word when any of its four nibbles is hovered
      const wordEl = el.parentElement!;
      const wordRange = new BitRange(Number(wordEl.dataset.bitpos), 16);
      this.addToHighlight(wordRange);

      if (pressing) {
        if (this.hexPressStart) {
          // Select all bits between current word and word when mouse was pressed
          const joined = this.hexPressStart.join(wordRange);
          this.setSelection(joined);
        } else if (this.hexFillerPressStart !== undefined) {
          // Use filler as a cursor and select until current word
          const joined = wordRange.joinBit(this.hexFillerPressStart);
          this.setSelection(joined);
        }
      }
    } else if (el.classList.contains('hexfiller')) {
      const fillerpos = Number(el.dataset.bitpos);
      if (pressing) {
        if (this.hexPressStart) {
          const joined = this.hexPressStart.joinBit(fillerpos);
          this.setSelection(joined);
        } else if (this.hexFillerPressStart !== undefined) {
          const joined = new BitRange(this.hexFillerPressStart, 0).joinBit(fillerpos);
          this.setSelection(joined);
        }
      }
    } else if (el.classList.contains('char')) {
      // Highlight byte when a character is hovered
      const charRange = new BitRange(Number(el.dataset.bitpos), 8);
      this.addToHighlight(charRange);

      if (pressing) {
        if (this.asciiPressStart) {
          const joined = this.asciiPressStart.join(charRange);
          this.setSelection(joined);
        } else if (this.asciiFillerPressStart !== undefined) {
          const joined = charRange.joinBit(this.asciiFillerPressStart);
          this.setSelection(joined);
        }
      }
    } else if (el.classList.contains('asciifiller')) {
      const fillerpos = Number(el.dataset.bitpos);
      if (pressing) {
        if (this.asciiPressStart) {
          const joined = this.asciiPressStart.joinBit(fillerpos);
          this.setSelection(joined);
        } else if (this.asciiFillerPressStart !== undefined) {
          const joined = new BitRange(this.asciiFillerPressStart, 0).joinBit(fillerpos);
          this.setSelection(joined);
        }
      }
    }
  }

  @HostListener('document:mouseup')
  onMouseUp() {
    this.linePressStart = undefined;
    this.hexPressStart = undefined;
    this.hexFillerPressStart = undefined;
    this.asciiPressStart = undefined;
    this.asciiFillerPressStart = undefined;
  }

  @HostListener('document:mouseout')
  onMouseOut() {
    this.clearHighlight();
  }

  /**
   * Returns a hex string of the entire input
   */
  public getHexString() {
    const model = this.model$.value;
    return model?.printHex();
  }

  private clearSelection() {
    for (const el of this.selectedEls) {
      el.classList.remove('selected');
    }
    this.selectedEls.length = 0;
  }

  private clearHighlight() {
    for (const el of this.highlightedEls) {
      el.classList.remove('hl');
    }
    this.highlightedEls.length = 0;
  }

  private addToHighlight(range: BitRange) {
    // Limit to max bitlength
    const model = this.model$.value;
    range = range.intersect(new BitRange(0, model!.bitlength))!;

    this.nibbleRefs.forEach(ref => {
      const nibbleEl: HTMLElement = ref.nativeElement;
      const nibbleRange = new BitRange(Number(nibbleEl.dataset.bitpos), 4);
      if (range.overlaps(nibbleRange)) {
        nibbleEl.classList.add('hl');
        this.highlightedEls.push(nibbleEl);
      }
    });
    this.hexFillerRefs.forEach(ref => {
      const fillerEl: HTMLElement = ref.nativeElement;
      const fillerPosition = Number(fillerEl.dataset.bitpos);
      if (!fillerEl.classList.contains('last')) {
        if (range.containsBitExclusive(fillerPosition)) {
          fillerEl.classList.add('hl');
          this.highlightedEls.push(fillerEl);
        }
      }
    });
    this.charRefs.forEach(ref => {
      const charEl: HTMLElement = ref.nativeElement;
      const charRange = new BitRange(Number(charEl.dataset.bitpos), 8);
      if (range.overlaps(charRange)) {
        charEl.classList.add('hl');
        this.highlightedEls.push(charEl);
      }
    });
  }

  private setSelection(range: BitRange) {
    // Limit to max bitlength
    const model = this.model$.value;
    range = range.intersect(new BitRange(0, model!.bitlength))!;

    this.clearSelection();
    this.nibbleRefs.forEach(ref => {
      const nibbleEl: HTMLElement = ref.nativeElement;
      const nibbleRange = new BitRange(Number(nibbleEl.dataset.bitpos), 4);
      if (range.overlaps(nibbleRange)) {
        nibbleEl.classList.add('selected');
        this.selectedEls.push(nibbleEl);
      }
    });
    this.hexFillerRefs.forEach(ref => {
      const fillerEl: HTMLElement = ref.nativeElement;
      const fillerPosition = Number(fillerEl.dataset.bitpos);
      if (!fillerEl.classList.contains('last')) {
        if (range.containsBitExclusive(fillerPosition)) {
          fillerEl.classList.add('selected');
          this.selectedEls.push(fillerEl);
        }
      }
    });
    this.charRefs.forEach(ref => {
      const charEl: HTMLElement = ref.nativeElement;
      const charRange = new BitRange(Number(charEl.dataset.bitpos), 8);
      if (range.overlaps(charRange)) {
        charEl.classList.add('selected');
        this.selectedEls.push(charEl);
      }
    });
  }
}
