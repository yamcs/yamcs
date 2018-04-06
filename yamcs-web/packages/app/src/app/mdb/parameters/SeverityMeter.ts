import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ViewChild,
  ElementRef,
  Input,
  OnChanges,
} from '@angular/core';
import { ParameterValue, AlarmRange } from '@yamcs/client';

const XMLNS = 'http://www.w3.org/2000/svg';

@Component({
  selector: 'app-severity-meter',
  template: `
    <svg #container
         width="100%" height="8px"
         viewBox="0 0 100 100"
         preserveAspectRatio="none"
         style="overflow: visible">
    </svg>`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SeverityMeter implements AfterViewInit, OnChanges {

  @Input()
  pval: ParameterValue;

  @ViewChild('container')
  private container: ElementRef;

  private criticalLowRange: SVGRectElement;
  private criticalHighRange: SVGRectElement;

  private indicator: SVGPolygonElement;

  ngOnChanges() {
    if (this.pval && this.indicator) {
      this.redraw();
    }
  }

  ngAfterViewInit() {
    const targetEl = this.container.nativeElement as SVGSVGElement;

    const fullRange = document.createElementNS(XMLNS, 'rect');
    fullRange.setAttribute('x', '0');
    fullRange.setAttribute('y', '0');
    fullRange.setAttribute('width', '100');
    fullRange.setAttribute('height', '50');
    fullRange.setAttribute('shape-rendering', 'crispEdges');
    fullRange.style.fill = '#00ff00';
    targetEl.appendChild(fullRange);

    this.criticalLowRange = document.createElementNS(XMLNS, 'rect');
    this.criticalLowRange.setAttribute('x', '0');
    this.criticalLowRange.setAttribute('y', '0');
    this.criticalLowRange.setAttribute('width', '0');
    this.criticalLowRange.setAttribute('height', '50');
    this.criticalLowRange.setAttribute('shape-rendering', 'crispEdges');
    this.criticalLowRange.style.fill = 'red';
    targetEl.appendChild(this.criticalLowRange);

    this.criticalHighRange = document.createElementNS(XMLNS, 'rect');
    this.criticalHighRange.setAttribute('x', '0');
    this.criticalHighRange.setAttribute('y', '0');
    this.criticalHighRange.setAttribute('width', '0');
    this.criticalHighRange.setAttribute('height', '50');
    this.criticalHighRange.setAttribute('shape-rendering', 'crispEdges');
    this.criticalHighRange.style.fill = 'red';
    targetEl.appendChild(this.criticalHighRange);

    this.indicator = document.createElementNS(XMLNS, 'polygon');
    this.indicator.classList.add('indicator');
    this.indicator.style.fill = 'black';
    this.indicator.style.visibility = 'hidden';
    targetEl.appendChild(this.indicator);

    if (this.pval) {
      this.redraw();
    }
  }

  private redraw() {
    let minLimit;
    let maxLimit;
    const rangeMap = new Map<string, AlarmRange>();
    if (this.pval.alarmRange) {
      for (const range of this.pval.alarmRange) {
        rangeMap.set(range.level, range);
        if (range.minInclusive !== undefined) {
          if (minLimit === undefined || range.minInclusive < minLimit) {
            minLimit = range.minInclusive;
          }
        }
        if (range.minExclusive !== undefined) {
          if (minLimit === undefined || range.minExclusive < minLimit) {
            minLimit = range.minExclusive;
          }
        }
        if (range.maxInclusive !== undefined) {
          if (maxLimit === undefined || range.maxInclusive > maxLimit) {
            maxLimit = range.maxInclusive;
          }
        }
        if (range.maxExclusive !== undefined) {
          if (maxLimit === undefined || range.maxExclusive > maxLimit) {
            maxLimit = range.maxExclusive;
          }
        }
      }
    }

    // Reset all
    this.criticalHighRange.setAttribute('width', '0');
    this.criticalLowRange.setAttribute('width', '0');
    this.indicator.style.visibility = 'hidden';

    if (minLimit !== undefined && maxLimit !== undefined && minLimit !== maxLimit) {
      const meterMin = minLimit - (maxLimit - minLimit) / 5;
      const meterMax = maxLimit + (maxLimit - minLimit) / 5;
      const ratio = 100 / (meterMax - meterMin);

      const criticalRange = rangeMap.get('CRITICAL');
      if (criticalRange) {
        if (criticalRange.minInclusive) {
          const width = (criticalRange.minInclusive - meterMin) * ratio;
          this.criticalLowRange.setAttribute('width', String(width));
        } else if (criticalRange.minExclusive) {
          const width = (criticalRange.minExclusive - meterMin) * ratio;
          this.criticalLowRange.setAttribute('width', String(width));
        }
        if (criticalRange.maxInclusive) {
          const x = (criticalRange.maxInclusive - meterMin) * ratio;
          this.criticalHighRange.setAttribute('x', String(x));
          this.criticalHighRange.setAttribute('width', String(100 - x));
        } else if (criticalRange.maxExclusive) {
          const x = (criticalRange.maxExclusive - meterMin) * ratio;
          this.criticalHighRange.setAttribute('x', String(x));
          this.criticalHighRange.setAttribute('width', String(100 - x));
        }
      }

      const currentValue = this.getNumericValue(this.pval);
      if (currentValue !== undefined) {
        const constrained = Math.min(Math.max(currentValue, meterMin), meterMax);
        const pos = (constrained - meterMin) * ratio;
        this.indicator.setAttribute('points', `${pos},25 ${pos - 3.5},100 ${pos + 3.5},100`);
        this.indicator.style.visibility = 'visible';
      } else {
        this.indicator.style.visibility = 'hidden';
      }
    }
  }

  private getNumericValue(pval: ParameterValue) {
    const engValue = pval.engValue;
    switch (engValue.type) {
      case 'FLOAT':
        return engValue.floatValue;
      case 'DOUBLE':
        return engValue.doubleValue;
      case 'UINT32':
        return engValue.uint32Value;
      case 'SINT32':
        return engValue.sint32Value;
      case 'UINT64':
        return engValue.uint64Value;
      case 'SINT64':
        return engValue.sint64Value;
    }
  }
}
