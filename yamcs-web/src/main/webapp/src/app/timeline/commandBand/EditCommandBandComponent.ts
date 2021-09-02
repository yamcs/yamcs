import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { BehaviorSubject } from 'rxjs';
import { TimelineBand } from '../../client/types/timeline';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-edit-command-band',
  templateUrl: './EditCommandBandComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditCommandBandComponent implements AfterViewInit {

  @Input()
  form: FormGroup;

  @Input()
  band: TimelineBand;

  formConfigured$ = new BehaviorSubject<boolean>(false);

  constructor(
    readonly yamcs: YamcsService,
    private changeDetection: ChangeDetectorRef,
  ) { }

  ngAfterViewInit() {
    this.formConfigured$.next(true);
    this.changeDetection.detectChanges();
  }
}
