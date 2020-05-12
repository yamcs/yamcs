import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Parameter, ParameterValue, Value } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-parameter-detail',
  templateUrl: './ParameterDetail.html',
  styleUrls: ['./ParameterDetail.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ParameterDetail implements OnChanges {

  @Input()
  parameter: Parameter;

  @Input()
  pval: ParameterValue;

  showRaw$ = new BehaviorSubject<boolean>(false);
  value$ = new BehaviorSubject<Value | null>(null);

  constructor(readonly yamcs: YamcsService) {
  }

  ngOnChanges() {
    if (this.pval) {
      if (this.showRaw$.value) {
        this.value$.next(this.pval.rawValue);
      } else {
        this.value$.next(this.pval.engValue);
      }
    }
  }

  showRawValue() {
    this.showRaw$.next(true);
    if (this.pval) {
      this.value$.next(this.pval.rawValue);
    }
  }

  showEngineeringValue() {
    this.showRaw$.next(false);
    if (this.pval) {
      this.value$.next(this.pval.engValue);
    }
  }
}
