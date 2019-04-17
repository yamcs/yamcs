import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, ViewChild } from '@angular/core';

const fontWeight = '500';
const slopeWidth = 10;

@Component({
  selector: 'app-slanted-label',
  host: {
    '[class.shake]': 'shake',
    '[class.highlight]': 'highlight',
    '[class.selectable]': 'selectable',
  },
  template: `
    <div id="wrapper">
      <svg #container
           style="overflow: visible; width: 100px; height: 32px;">
        <text #text y="${16 + 1}" dominant-baseline="middle" text-anchor="middle"
              fill="white"
              style="font-size: 14px; font-weight: ${fontWeight}">
          <ng-content></ng-content>
        </text>
        <polygon #outline></polygon>
        <polygon #iconHolder></polygon>
      </svg>
      <div *ngIf="icon" id="icon-wrapper" style="position: absolute; top: 0; left: ${slopeWidth}px;">
        <mat-icon class="icon16" [style.color]="iconColor">{{ icon }}</mat-icon>
      </div>
    </div>`,
  styleUrls: ['./SlantedLabel.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SlantedLabel implements AfterViewInit {

  @Input()
  color = 'lime';

  @Input()
  icon: string;

  @Input()
  iconColor = 'white';

  @Input()
  shake = false;

  @Input()
  highlight = false;

  @Input()
  selectable = false;

  @Input()
  margin = 10;

  @ViewChild('container')
  private container: ElementRef;

  @ViewChild('text')
  private text: ElementRef;

  @ViewChild('outline')
  private outline: ElementRef;

  @ViewChild('iconHolder')
  private iconHolder: ElementRef;

  async ngAfterViewInit() {
    const fontFace = 'Liberation Sans';
    try { // Times out after 3 seconds
      await Promise.all([
        new FontFaceObserver('Roboto', { weight: fontWeight, style: 'normal' }).load(),
      ]);
    } catch {
      // tslint:disable-next-line:no-console
      console.warn(`Failed to load all font variants for '${fontFace}'. Font metric calculations may not be accurate.`);
    }

    this.redraw();
  }

  redraw() {
    const targetEl = this.container.nativeElement as SVGSVGElement;
    const textEl = this.text.nativeElement as SVGTextElement;
    const outlineEl = this.outline.nativeElement as SVGPolygonElement;
    const iconHolderEl = this.iconHolder.nativeElement as SVGPolygonElement;

    const textBbox = textEl.getBBox();
    const iconWidth = this.icon ? 16 + slopeWidth : 0;
    const fullWidth = textBbox.width + slopeWidth + slopeWidth + iconWidth + (2 * this.margin);

    targetEl.style.width = `${fullWidth}px`;
    let points = `${slopeWidth},0.5`;
    points += ` ${fullWidth},0.5`;
    points += ` ${fullWidth - slopeWidth},${32 - 0.5}`;
    points += ` 0,${32 - 0.5}`;

    outlineEl.setAttribute('points', points);
    outlineEl.setAttribute('style', `fill: none; stroke: ${this.color}; stroke-width: 1`);

    textEl.setAttribute('x', `${iconWidth + (fullWidth - iconWidth) / 2}`);
    textEl.setAttribute('fill', this.color);

    if (this.icon) {
      points = `${slopeWidth},0`;
      points += ` ${iconWidth + slopeWidth},0`;
      points += ` ${iconWidth},${32}`;
      points += ` 0,${32}`;

      iconHolderEl.setAttribute('points', points);
      iconHolderEl.setAttribute('fill', this.color);
    }
  }
}
