import { AfterViewInit, ChangeDetectionStrategy, Component, ElementRef, Input, OnChanges, ViewChild } from '@angular/core';
import { AlarmRange, ParameterValue } from '@yamcs/webapp-sdk';

const XMLNS = 'http://www.w3.org/2000/svg';

@Component({
  standalone: true,
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
export class SeverityMeterComponent implements AfterViewInit, OnChanges {

  @Input()
  pval: ParameterValue;

  @ViewChild('container', { static: true })
  private container: ElementRef;

  private watchLowRange: SVGRectElement;
  private watchHighRange: SVGRectElement;
  private warningLowRange: SVGRectElement;
  private warningHighRange: SVGRectElement;
  private distressLowRange: SVGRectElement;
  private distressHighRange: SVGRectElement;
  private criticalLowRange: SVGRectElement;
  private criticalHighRange: SVGRectElement;
  private severeLowRange: SVGRectElement;
  private severeHighRange: SVGRectElement;

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

    this.watchLowRange = document.createElementNS(XMLNS, 'rect');
    this.watchLowRange.setAttribute('x', '0');
    this.watchLowRange.setAttribute('y', '0');
    this.watchLowRange.setAttribute('width', '0');
    this.watchLowRange.setAttribute('height', '50');
    this.watchLowRange.setAttribute('shape-rendering', 'crispEdges');
    this.watchLowRange.style.fill = '#ffff98';
    targetEl.appendChild(this.watchLowRange);

    this.watchHighRange = document.createElementNS(XMLNS, 'rect');
    this.watchHighRange.setAttribute('x', '0');
    this.watchHighRange.setAttribute('y', '0');
    this.watchHighRange.setAttribute('width', '0');
    this.watchHighRange.setAttribute('height', '50');
    this.watchHighRange.setAttribute('shape-rendering', 'crispEdges');
    this.watchHighRange.style.fill = '#ffff98';
    targetEl.appendChild(this.watchHighRange);

    this.warningLowRange = document.createElementNS(XMLNS, 'rect');
    this.warningLowRange.setAttribute('x', '0');
    this.warningLowRange.setAttribute('y', '0');
    this.warningLowRange.setAttribute('width', '0');
    this.warningLowRange.setAttribute('height', '50');
    this.warningLowRange.setAttribute('shape-rendering', 'crispEdges');
    this.warningLowRange.style.fill = '#ffff00';
    targetEl.appendChild(this.warningLowRange);

    this.warningHighRange = document.createElementNS(XMLNS, 'rect');
    this.warningHighRange.setAttribute('x', '0');
    this.warningHighRange.setAttribute('y', '0');
    this.warningHighRange.setAttribute('width', '0');
    this.warningHighRange.setAttribute('height', '50');
    this.warningHighRange.setAttribute('shape-rendering', 'crispEdges');
    this.warningHighRange.style.fill = '#ffff00';
    targetEl.appendChild(this.warningHighRange);

    this.distressLowRange = document.createElementNS(XMLNS, 'rect');
    this.distressLowRange.setAttribute('x', '0');
    this.distressLowRange.setAttribute('y', '0');
    this.distressLowRange.setAttribute('width', '0');
    this.distressLowRange.setAttribute('height', '50');
    this.distressLowRange.setAttribute('shape-rendering', 'crispEdges');
    this.distressLowRange.style.fill = '#ff6600';
    targetEl.appendChild(this.distressLowRange);

    this.distressHighRange = document.createElementNS(XMLNS, 'rect');
    this.distressHighRange.setAttribute('x', '0');
    this.distressHighRange.setAttribute('y', '0');
    this.distressHighRange.setAttribute('width', '0');
    this.distressHighRange.setAttribute('height', '50');
    this.distressHighRange.setAttribute('shape-rendering', 'crispEdges');
    this.distressHighRange.style.fill = '#ff6600';
    targetEl.appendChild(this.distressHighRange);

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

    this.severeLowRange = document.createElementNS(XMLNS, 'rect');
    this.severeLowRange.setAttribute('x', '0');
    this.severeLowRange.setAttribute('y', '0');
    this.severeLowRange.setAttribute('width', '0');
    this.severeLowRange.setAttribute('height', '50');
    this.severeLowRange.setAttribute('shape-rendering', 'crispEdges');
    this.severeLowRange.style.fill = '#800000';
    targetEl.appendChild(this.severeLowRange);

    this.severeHighRange = document.createElementNS(XMLNS, 'rect');
    this.severeHighRange.setAttribute('x', '0');
    this.severeHighRange.setAttribute('y', '0');
    this.severeHighRange.setAttribute('width', '0');
    this.severeHighRange.setAttribute('height', '50');
    this.severeHighRange.setAttribute('shape-rendering', 'crispEdges');
    this.severeHighRange.style.fill = '#800000';
    targetEl.appendChild(this.severeHighRange);

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

      const watchRange = rangeMap.get('WATCH');
      if (watchRange) {
        if (watchRange.minInclusive) {
          const width = (watchRange.minInclusive - meterMin) * ratio;
          this.watchLowRange.setAttribute('width', String(width));
        } else if (watchRange.minExclusive) {
          const width = (watchRange.minExclusive - meterMin) * ratio;
          this.watchLowRange.setAttribute('width', String(width));
        }
        if (watchRange.maxInclusive) {
          const x = (watchRange.maxInclusive - meterMin) * ratio;
          this.watchHighRange.setAttribute('x', String(x));
          this.watchHighRange.setAttribute('width', String(100 - x));
        } else if (watchRange.maxExclusive) {
          const x = (watchRange.maxExclusive - meterMin) * ratio;
          this.watchHighRange.setAttribute('x', String(x));
          this.watchHighRange.setAttribute('width', String(100 - x));
        }
      }

      const warningRange = rangeMap.get('WARNING');
      if (warningRange) {
        if (warningRange.minInclusive) {
          const width = (warningRange.minInclusive - meterMin) * ratio;
          this.warningLowRange.setAttribute('width', String(width));
        } else if (warningRange.minExclusive) {
          const width = (warningRange.minExclusive - meterMin) * ratio;
          this.warningLowRange.setAttribute('width', String(width));
        }
        if (warningRange.maxInclusive) {
          const x = (warningRange.maxInclusive - meterMin) * ratio;
          this.warningHighRange.setAttribute('x', String(x));
          this.warningHighRange.setAttribute('width', String(100 - x));
        } else if (warningRange.maxExclusive) {
          const x = (warningRange.maxExclusive - meterMin) * ratio;
          this.warningHighRange.setAttribute('x', String(x));
          this.warningHighRange.setAttribute('width', String(100 - x));
        }
      }

      const distressRange = rangeMap.get('DISTRESS');
      if (distressRange) {
        if (distressRange.minInclusive) {
          const width = (distressRange.minInclusive - meterMin) * ratio;
          this.distressLowRange.setAttribute('width', String(width));
        } else if (distressRange.minExclusive) {
          const width = (distressRange.minExclusive - meterMin) * ratio;
          this.distressLowRange.setAttribute('width', String(width));
        }
        if (distressRange.maxInclusive) {
          const x = (distressRange.maxInclusive - meterMin) * ratio;
          this.distressHighRange.setAttribute('x', String(x));
          this.distressHighRange.setAttribute('width', String(100 - x));
        } else if (distressRange.maxExclusive) {
          const x = (distressRange.maxExclusive - meterMin) * ratio;
          this.distressHighRange.setAttribute('x', String(x));
          this.distressHighRange.setAttribute('width', String(100 - x));
        }
      }

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

      const severeRange = rangeMap.get('SEVERE');
      if (severeRange) {
        if (severeRange.minInclusive) {
          const width = (severeRange.minInclusive - meterMin) * ratio;
          this.severeLowRange.setAttribute('width', String(width));
        } else if (severeRange.minExclusive) {
          const width = (severeRange.minExclusive - meterMin) * ratio;
          this.severeLowRange.setAttribute('width', String(width));
        }
        if (severeRange.maxInclusive) {
          const x = (severeRange.maxInclusive - meterMin) * ratio;
          this.severeHighRange.setAttribute('x', String(x));
          this.severeHighRange.setAttribute('width', String(100 - x));
        } else if (severeRange.maxExclusive) {
          const x = (severeRange.maxExclusive - meterMin) * ratio;
          this.severeHighRange.setAttribute('x', String(x));
          this.severeHighRange.setAttribute('width', String(100 - x));
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
