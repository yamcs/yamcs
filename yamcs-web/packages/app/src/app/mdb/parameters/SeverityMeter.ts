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
    <svg #container width="50%" height="4px" viewBox="0 0 100 100" preserveAspectRatio="none">
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

  private indicator: SVGLineElement;

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
    fullRange.setAttribute('height', '100');
    fullRange.setAttribute('shape-rendering', 'crispEdges');
    fullRange.style.fill = 'green';
    fullRange.style.opacity = '0.2';
    targetEl.appendChild(fullRange);

    this.criticalLowRange = document.createElementNS(XMLNS, 'rect');
    this.criticalLowRange.setAttribute('x', '0');
    this.criticalLowRange.setAttribute('y', '0');
    this.criticalLowRange.setAttribute('width', '0');
    this.criticalLowRange.setAttribute('height', '100');
    this.criticalLowRange.setAttribute('shape-rendering', 'crispEdges');
    this.criticalLowRange.style.fill = 'red';
    this.criticalLowRange.style.opacity = '0.6';
    targetEl.appendChild(this.criticalLowRange);

    this.criticalHighRange = document.createElementNS(XMLNS, 'rect');
    this.criticalHighRange.setAttribute('x', '0');
    this.criticalHighRange.setAttribute('y', '0');
    this.criticalHighRange.setAttribute('width', '0');
    this.criticalHighRange.setAttribute('height', '100');
    this.criticalHighRange.setAttribute('shape-rendering', 'crispEdges');
    this.criticalHighRange.style.fill = 'red';
    this.criticalHighRange.style.opacity = '0.6';
    targetEl.appendChild(this.criticalHighRange);

    this.indicator = document.createElementNS(XMLNS, 'line');
    this.indicator.setAttribute('x1', '-10');
    this.indicator.setAttribute('y1', '0');
    this.indicator.setAttribute('x2', '-10');
    this.indicator.setAttribute('y2', '100');
    this.indicator.setAttribute('shape-rendering', 'crispEdges');
    this.indicator.setAttribute('vector-effect', 'non-scaling-stroke');
    this.indicator.style.stroke = 'black';
    this.indicator.style.strokeWidth = '2';
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
    this.indicator.setAttribute('x1', '-10');
    this.indicator.setAttribute('x2', '-10');

    if (minLimit !== undefined && maxLimit !== undefined && minLimit !== maxLimit) {
      const meterMin = minLimit - (maxLimit - minLimit) / 5;
      const meterMax = maxLimit + (maxLimit - minLimit) / 5;
      const ratio = 100 / (meterMax - meterMin);

      const criticalRange = rangeMap.get('CRITICAL');
      if (criticalRange) {
        if (criticalRange.minInclusive) {
          this.criticalLowRange.setAttribute('width', String((criticalRange.minInclusive - meterMin) * ratio));
        } else if (criticalRange.minExclusive) {
          this.criticalLowRange.setAttribute('width', String((criticalRange.minExclusive - meterMin) * ratio));
        }
        if (criticalRange.maxInclusive) {
          this.criticalHighRange.setAttribute('x', String((criticalRange.maxInclusive - meterMin) * ratio));
          this.criticalHighRange.setAttribute('width', String((criticalRange.maxInclusive - meterMin) * ratio));
        } else if (criticalRange.maxExclusive) {
          this.criticalHighRange.setAttribute('x', String((criticalRange.maxExclusive - meterMin) * ratio));
          this.criticalHighRange.setAttribute('width', String((criticalRange.maxExclusive - meterMin) * ratio));
        }
      }

      const currentValue = this.getNumericValue(this.pval);
      if (currentValue !== undefined) {
        const constrained = Math.min(Math.max(currentValue, meterMin), meterMax);
        const rescaledValue = (constrained - meterMin) * ratio;
        this.indicator.setAttribute('x1', String(rescaledValue));
        this.indicator.setAttribute('x2', String(rescaledValue));
      } else {
        this.indicator.setAttribute('x1', String(-10));
        this.indicator.setAttribute('x2', String(-10));
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
