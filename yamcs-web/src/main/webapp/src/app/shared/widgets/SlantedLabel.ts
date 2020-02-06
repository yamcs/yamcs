import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, OnDestroy, ViewChild } from '@angular/core';
import { Subscription } from 'rxjs';
import { Synchronizer } from '../../core/services/Synchronizer';

const fontWeight = '500';
const slopeWidth = 10;

@Component({
  selector: 'app-slanted-label',
  host: {
    '[class.highlight]': 'highlight',
    '[class.selectable]': 'selectable',
  },
  template: `
    <div id="wrapper">
      <svg #container
           style="overflow: visible; width: 100px; height: 32px;">
        <polygon #outline></polygon>
        <text #text y="${16 + 1}" dominant-baseline="middle" text-anchor="middle"
              fill="white"
              style="font-size: 14px; font-weight: ${fontWeight}">
          <ng-content></ng-content>
        </text>
        <polygon #iconHolder></polygon>
      </svg>
      <div *ngIf="icon" id="icon-wrapper" style="position: absolute; top: 0; left: ${slopeWidth}px;">
        <mat-icon class="icon16" [style.color]="iconColor">{{ icon }}</mat-icon>
      </div>
    </div>`,
  styleUrls: ['./SlantedLabel.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SlantedLabel implements AfterViewInit, OnDestroy {

  @Input()
  color = 'lime';

  @Input()
  icon: string;

  @Input()
  iconColor = 'black';

  @Input()
  highlight = false;

  @Input()
  selectable = false;

  @Input()
  margin = 10;

  @ViewChild('container', { static: true })
  private container: ElementRef;

  @ViewChild('text', { static: true })
  private text: ElementRef;

  @ViewChild('outline', { static: true })
  private outline: ElementRef;

  @ViewChild('iconHolder', { static: true })
  private iconHolder: ElementRef;

  private syncSubscription: Subscription;

  constructor(synchronizer: Synchronizer) {
    let toggle = true;
    this.syncSubscription = synchronizer.sync(() => {
      toggle = !toggle;
      if (toggle && this.highlight) {
        this.outline.nativeElement.style.fill = this.color;
        this.text.nativeElement.style.fill = 'inherit';
      } else {
        this.outline.nativeElement.style.fill = 'none';
        this.outline.nativeElement.style.stroke = this.color;
        this.iconHolder.nativeElement.style.fill = this.color;
        this.text.nativeElement.style.fill = this.color;
      }
    });
  }

  async ngAfterViewInit() {
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

  ngOnDestroy() {
    if (this.syncSubscription) {
      this.syncSubscription.unsubscribe();
    }
  }
}
