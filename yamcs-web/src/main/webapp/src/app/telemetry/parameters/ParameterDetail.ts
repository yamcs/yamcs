import { ChangeDetectionStrategy, Component, Input, OnChanges } from '@angular/core';
import { BehaviorSubject } from 'rxjs';
import { Member, Parameter, ParameterValue } from '../../client';
import { YamcsService } from '../../core/services/YamcsService';
import { EntryForOffsetPipe } from '../../shared/pipes/EntryForOffsetPipe';

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
  offset: string;

  @Input()
  pval: ParameterValue;

  // A Parameter or a Member depending on whether the user is visiting
  // nested entries of an aggregate or array.
  entry$ = new BehaviorSubject<Parameter | Member | null>(null);

  constructor(readonly yamcs: YamcsService, private entryForOffsetPipe: EntryForOffsetPipe) {
  }

  ngOnChanges() {
    if (this.parameter) {
      if (this.offset) {
        const entry = this.entryForOffsetPipe.transform(this.parameter, this.offset);
        this.entry$.next(entry);
      } else {
        this.entry$.next(this.parameter);
      }
    } else {
      this.entry$.next(null);
    }
  }
}
