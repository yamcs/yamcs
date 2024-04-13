import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, OnChanges, ViewChild } from '@angular/core';

export const ON_COLOR = 'rgb(0,255,0)';
export const OFF_COLOR = 'rgb(0,100,0)';

export let SEQ = 0;

@Component({
  standalone: true,
  selector: 'ya-led',
  template: `
        <div #container [style.opacity]="fade ? 0.3 : 1"
                        [style.width]="width + 'px'"
                        [style.height]="height + 'px'"
                        style="display: inline-block; line-height: 0">
        </div>`,
  styleUrl: './led.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class YaLed implements AfterViewInit, OnChanges {

  @Input()
  width = 16;

  @Input()
  height = 16;

  @Input()
  color = ON_COLOR;

  @Input()
  borderColor = 'rgb(150,150,150)';

  @Input()
  border = 2;

  @Input()
  fade = true;

  private id = SEQ++;

  @ViewChild('container')
  private containerEl: ElementRef;

  ngAfterViewInit() {
    this.render();
  }

  ngOnChanges() {
    if (this.containerEl) {
      this.render();
    }
  }

  private render() {
    const innerWidth = this.width - (2 * this.border);
    const innerHeight = this.height - (2 * this.border);
    const content = `
            <svg width="${this.width}" height="${this.height}">
                <defs>
                    <linearGradient id="${this.id}g1" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stop-color="${this.borderColor}" stop-opacity="1" />
                        <stop offset="100%" stop-color="${this.borderColor}" stop-opacity="0" />
                    </linearGradient>
                    <linearGradient id="${this.id}g2" x1="0%" y1="0%" x2="100%" y2="100%">
                        <stop offset="0%" stop-color="white" stop-opacity="1" />
                        <stop offset="100%" stop-color="${this.borderColor}" stop-opacity="0" />
                    </linearGradient>
                </defs>
                <ellipse cx="${this.width / 2}" cy="${this.height / 2}"
                         rx="${this.width / 2}" ry="${this.height / 2}"
                         fill="white" />
                <ellipse cx="${this.width / 2}" cy="${this.height / 2}"
                         rx="${this.width / 2}" ry="${this.height / 2}"
                         fill="url(${window.location.href}#${this.id}g1)" />
                <ellipse cx="${this.width / 2}" cy="${this.height / 2}"
                         rx="${innerWidth / 2}" ry="${innerHeight / 2}"
                         fill="${this.color}" />
                <ellipse cx="${this.width / 2}" cy="${this.height / 2}"
                         rx="${innerWidth / 2}" ry="${innerHeight / 2}"
                         fill="url(${window.location.href}#${this.id}g2)" />
                </svg>
        `;
    this.containerEl.nativeElement.innerHTML = content;
  }
}
