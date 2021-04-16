import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, HostListener, Input, OnChanges, ViewChild } from '@angular/core';
import { BitRange } from '../BitRange';
import { EventHandler } from '../draw/EventHandler';
import { Graphics } from '../draw/Graphics';
import { HexModel, Line } from './model';

@Component({
  selector: 'app-hex',
  templateUrl: './Hex.html',
  styleUrls: ['./Hex.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Hex implements AfterViewInit, OnChanges {

  @ViewChild('canvasEl')
  canvasEl: ElementRef;

  @Input()
  base64String: string;

  @Input()
  fontSize = 10;

  private charWidth: number;

  private model: HexModel;
  private g: Graphics;
  private dirty = true;

  private highlight?: BitRange;
  private selection?: BitRange;
  private pressStart?: BitRange;

  ngAfterViewInit() {
    const el = this.canvasEl.nativeElement as HTMLCanvasElement;
    const ctx = el.getContext('2d')!;
    ctx.font = `${this.fontSize}px monospace`;
    this.charWidth = ctx.measureText('0').width;

    this.g = new Graphics(el);
    window.requestAnimationFrame(() => this.redraw());
    new EventHandler(this.g.canvas, this.g.hitCanvas);
  }

  ngOnChanges() {
    const raw = atob(this.base64String);
    this.model = new HexModel(raw);
    this.highlight = undefined;
    this.selection = undefined;
    this.pressStart = undefined;
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

  private redraw() {
    window.requestAnimationFrame(() => this.redraw());

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
      mouseOut: () => {
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
      font: `${this.fontSize}px monospace`,
      color: '#777777',
      text,
    });
  }

  private drawHex(line: Line, y: number) {
    let x = this.charWidth * (line.charCountHex + ': ').length;

    for (const component of line.hexComponents) {
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
          mouseOut: () => {
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
          if (this.highlight && this.highlight.overlaps(nibble.range)) {
            bgColor = '#aec5d9';
          } else if (this.selection && this.selection.overlaps(nibble.range)) {
            bgColor = '#5787b1';
            fgColor = '#ffffff';
          }
          if (bgColor) {
            this.g.fillRect({
              x: Math.floor(x),
              y: Math.floor(y),
              width: Math.ceil(this.charWidth),
              height: this.fontSize,
              color: bgColor,
            });
          }
          this.g.fillText({
            x, y,
            baseline: 'top',
            align: 'left',
            font: `${this.fontSize}px monospace`,
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
        if (this.highlight && this.highlight.containsBitExclusive(component.bitpos)) {
          bgColor = '#aec5d9';
        } else if (this.selection && this.selection.containsBitExclusive(component.bitpos)) {
          bgColor = '#5787b1';
        }
        if (bgColor) {
          this.g.fillRect({
            x: Math.floor(x),
            y: Math.floor(y),
            width: Math.ceil(this.charWidth * component.content.length),
            height: this.fontSize,
            color: bgColor,
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
            mouseOut: () => {
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
          if (this.highlight && this.highlight.overlaps(c.range)) {
            bgColor = '#aec5d9';
          } else if (this.selection && this.selection.overlaps(c.range)) {
            bgColor = '#5787b1';
            fgColor = '#ffffff';
          }
          if (bgColor) {
            this.g.fillRect({
              x: Math.floor(x),
              y: Math.floor(y),
              width: Math.ceil(this.charWidth),
              height: this.fontSize,
              color: bgColor,
            });
          }
          this.g.fillText({
            x, y,
            baseline: 'top',
            align: 'left',
            font: `${this.fontSize}px monospace`,
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
}
