import { APP_BASE_HREF } from '@angular/common';
import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, Inject, Input, OnChanges, OnDestroy, ViewChild } from '@angular/core';
import { EventHandler, Graphics } from '@fqqb/timeline';
import { BitRange } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { HexModel, Line } from './model';

@Component({
  standalone: true,
  selector: 'app-hex',
  templateUrl: './hex.component.html',
  styleUrl: './hex.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HexComponent implements AfterViewInit, OnChanges, OnDestroy {

  fontPreloaded$: Promise<boolean>;

  @ViewChild('canvasEl')
  canvasEl: ElementRef;

  @Input()
  base64String?: string | null;

  @Input()
  fontSize = 10;

  public highlighted$ = new BehaviorSubject<BitRange | null>(null);
  public selection$ = new BehaviorSubject<BitRange | null>(null);

  private charWidth: number;

  private model: HexModel;
  private g: Graphics;
  private dirty = true;

  private _highlight?: BitRange;
  private _selection?: BitRange;
  private pressStart?: BitRange;

  private mediaQueryList?: MediaQueryList;
  private mediaQueryListEventListener: () => void;
  private animationFrameRequest?: number;

  constructor(@Inject(APP_BASE_HREF) baseHref: string) {
    const resourceUrl = `url(${baseHref}media/RobotoMono-Regular.woff2)`;
    this.fontPreloaded$ = new Promise(resolve => {
      const robotoMono = new FontFace('Roboto Mono', resourceUrl);
      robotoMono.load().then(() => resolve(true)).catch(() => resolve(false));
    });
  }

  ngAfterViewInit() {
    this.fontPreloaded$.then(() => this.initCanvas());
  }

  get highlight() {
    return this._highlight;
  }

  set highlight(_highlight: BitRange | undefined) {
    this._highlight = _highlight;
    if ((_highlight || null) !== this.highlighted$.value) {
      this.highlighted$.next(_highlight || null);
    }
  }

  get selection() {
    return this._selection;
  }

  set selection(_selection: BitRange | undefined) {
    this._selection = _selection;
    if ((_selection || null) !== this.selection$.value) {
      this.selection$.next(_selection || null);
    }
  }

  private initCanvas() {
    const el = this.canvasEl.nativeElement as HTMLCanvasElement;
    const ctx = el.getContext('2d')!;
    ctx.font = `${this.fontSize}px 'Roboto Mono', monospace`;
    this.charWidth = ctx.measureText('0').width;

    this.g = new Graphics(el);

    new EventHandler(this.g.canvas, this.g.hitCanvas);

    this.mediaQueryListEventListener = () => {
      this.dirty = true;
      this.mediaQueryList = matchMedia(`(resolution: ${window.devicePixelRatio}dppx)`);
      this.mediaQueryList.addEventListener('change', this.mediaQueryListEventListener, { once: true });
    };
    this.mediaQueryListEventListener();

    this.animationFrameRequest = window.requestAnimationFrame(() => this.step());
  }

  ngOnChanges() {
    const raw = window.atob(this.base64String ?? '');
    this.model = new HexModel(raw);
    this.highlight = undefined;
    this.selection = undefined;
    this.pressStart = undefined;
    this.dirty = true;
  }

  public setHighlight(range: BitRange | null) {
    if (range) {
      this.highlight = range;
    } else {
      this.highlight = undefined;
    }
    this.dirty = true;
  }

  public setSelection(range: BitRange | null) {
    if (range) {
      this.selection = range;
    } else {
      this.selection = undefined;
    }
    this.dirty = true;
  }

  @HostListener('document:mouseup')
  onMouseUp() {
    this.pressStart = undefined;
  }

  @HostListener('document:mouseout')
  onMouseOut() {
    this.highlight = undefined;
  }

  private step() {
    this.animationFrameRequest = window.requestAnimationFrame(() => this.step());

    if (!this.dirty) {
      return;
    }

    const lineWidth = Math.ceil(this.charWidth * (6 + 39 + 18));
    this.g.resize(lineWidth, this.fontSize * this.model.lines.length);
    this.g.ctx.clearRect(0, 0, this.g.canvas.width, this.g.canvas.height);
    this.g.clearHitCanvas();

    let y = 0;
    for (const line of this.model.lines) {
      this.drawCharcount(line, y);
      this.drawHex(line, y);
      this.drawAscii(line, y);
      y += this.fontSize;
    }

    this.dirty = false;
  }

  private drawCharcount(line: Line, y: number) {
    const text = line.charCountHex + ': ';

    this.g.addHitRegion({
      id: line.id,
      mouseDown: () => {
        this.pressStart = line.range;
        this.selection = line.range;
        this.dirty = true;
      },
      mouseEnter: () => {
        this.highlight = line.range;
        this.dirty = true;
      },
      mouseLeave: () => {
        this.highlight = undefined;
        this.dirty = true;
      },
      mouseMove: (pressing) => {
        if (pressing && this.pressStart) {
          const joined = this.pressStart.join(line.range);
          this.selection = joined;
          this.dirty = true;
        }
      }
    }).addRect(0, y, this.charWidth * text.length, this.fontSize);

    this.g.fillText({
      x: 0,
      y,
      baseline: 'top',
      align: 'left',
      font: `${this.fontSize}px 'Roboto Mono', monospace`,
      color: '#777777',
      text,
    });
  }

  private drawHex(line: Line, y: number) {
    let x = this.charWidth * (line.charCountHex + ': ').length;

    for (let i = 0; i < line.hexComponents.length; i++) {
      const component = line.hexComponents[i];
      if (component.type === 'word') {

        // Highlight entire word when any of its four nibbles is hovered
        this.g.addHitRegion({
          id: component.id,
          mouseDown: () => {
            this.pressStart = component.range;
            this.selection = component.range;
            this.dirty = true;
          },
          mouseEnter: () => {
            this.highlight = component.range;
            this.dirty = true;
          },
          mouseLeave: () => {
            this.highlight = undefined;
            this.dirty = true;
          },
          mouseMove: (pressing) => {
            if (pressing && this.pressStart) {
              const joined = this.pressStart.join(component.range);
              this.selection = joined;
              this.dirty = true;
            }
          }
        }).addRect(x, y, component.nibbles.length * this.charWidth, this.fontSize);

        for (const nibble of component.nibbles) {
          let bgColor;
          let fgColor = '#000000';
          if (!this.pressStart && this.highlight && this.highlight.overlaps(nibble.range)) {
            bgColor = 'lightgrey';
          } else if (this.selection && this.selection.overlaps(nibble.range)) {
            bgColor = '#009e87';
            fgColor = '#ffffff';
          }
          if (bgColor) {
            this.g.fillRect({
              x: Math.floor(x),
              y: Math.floor(y),
              width: Math.ceil(this.charWidth),
              height: this.fontSize,
              fill: bgColor,
            });
          }
          this.g.fillText({
            x, y,
            baseline: 'top',
            align: 'left',
            font: `${this.fontSize}px 'Roboto Mono', monospace`,
            color: fgColor,
            text: nibble.content,
          });
          x += this.charWidth;
        }
      } else if (component.type === 'filler') {
        this.g.addHitRegion({
          id: component.id,
          mouseDown: () => {
            this.pressStart = new BitRange(component.bitpos, 0);
          },
          mouseMove: (pressing) => {
            if (pressing && this.pressStart) {
              const joined = this.pressStart.joinBit(component.bitpos);
              this.selection = joined;
              this.dirty = true;
            }
          }
        }).addRect(x, y, component.content.length * this.charWidth, this.fontSize);

        let bgColor;
        if (!this.pressStart && this.highlight && this.highlight.containsBitExclusive(component.bitpos)) {
          bgColor = 'lightgrey';
        } else if (this.selection && this.selection.containsBitExclusive(component.bitpos)) {
          bgColor = '#009e87';
        }
        if (bgColor && i !== line.hexComponents.length - 1) {
          this.g.fillRect({
            x: Math.floor(x),
            y: Math.floor(y),
            width: Math.ceil(this.charWidth * component.content.length),
            height: this.fontSize,
            fill: bgColor,
          });
        }
        x += component.content.length * this.charWidth;
      }
    }
  }

  private drawAscii(line: Line, y: number) {
    let x = (6 + 39 + 2) * this.charWidth;

    for (const component of line.asciiComponents) {
      if (component.type === 'word') {

        for (const c of component.chars) {
          // Highlight byte when a character is hovered
          this.g.addHitRegion({
            id: component.id,
            mouseDown: () => {
              this.pressStart = c.range;
              this.selection = c.range;
              this.dirty = true;
            },
            mouseEnter: () => {
              this.highlight = c.range;
              this.dirty = true;
            },
            mouseLeave: () => {
              this.highlight = undefined;
              this.dirty = true;
            },
            mouseMove: (pressing) => {
              if (pressing && this.pressStart) {
                const joined = this.pressStart.join(c.range);
                this.selection = joined;
                this.dirty = true;
              }
            }
          }).addRect(x, y, this.charWidth, this.fontSize);

          let bgColor;
          let fgColor = '#777';
          if (!this.pressStart && this.highlight && this.highlight.overlaps(c.range)) {
            bgColor = 'lightgrey';
          } else if (this.selection && this.selection.overlaps(c.range)) {
            bgColor = '#009e87';
            fgColor = '#ffffff';
          }
          if (bgColor) {
            this.g.fillRect({
              x: Math.floor(x),
              y: Math.floor(y),
              width: Math.ceil(this.charWidth),
              height: this.fontSize,
              fill: bgColor,
            });
          }
          this.g.fillText({
            x, y,
            baseline: 'top',
            align: 'left',
            font: `${this.fontSize}px 'Roboto Mono', monospace`,
            color: fgColor,
            text: c.content,
          });
          x += this.charWidth;
        }
      } else if (component.type === 'filler') {
        this.g.addHitRegion({
          id: component.id,
          mouseDown: () => {
            this.pressStart = new BitRange(component.bitpos, 0);
          },
          mouseMove: (pressing) => {
            if (pressing && this.pressStart) {
              const joined = this.pressStart.joinBit(component.bitpos);
              this.selection = joined;
              this.dirty = true;
            }
          }
        }).addRect(x, y, component.content.length * this.charWidth, this.fontSize);
      }
    }
  }

  ngOnDestroy() {
    this.mediaQueryList?.removeEventListener('change', this.mediaQueryListEventListener);
    this.animationFrameRequest && window.cancelAnimationFrame(this.animationFrameRequest);
  }
}
