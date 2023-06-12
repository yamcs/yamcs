import { AfterViewInit, ChangeDetectionStrategy, ChangeDetectorRef, Component, Input } from '@angular/core';
import { UntypedFormGroup } from '@angular/forms';
import { TimelineBand } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { YamcsService } from '../../core/services/YamcsService';

@Component({
  selector: 'app-edit-command-band',
  templateUrl: './EditCommandBandComponent.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EditCommandBandComponent implements AfterViewInit {

  @Input()
  form: UntypedFormGroup;

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
