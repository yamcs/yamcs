import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnDestroy } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { MessageService, ParameterList, UpdateParameterListRequest, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject, Subscription } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { SelectParameterDialogComponent } from '../../../shared/select-parameter-dialog/select-parameter-dialog.component';

@Component({
  standalone: true,
  templateUrl: './edit-parameter-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class EditParameterListComponent implements OnDestroy {

  form: UntypedFormGroup;
  plist$: Promise<ParameterList>;
  private plist: ParameterList;

  patterns$ = new BehaviorSubject<string[]>([]);

  dirty$ = new BehaviorSubject<boolean>(false);
  private formSubscription: Subscription;

  constructor(
    formBuilder: UntypedFormBuilder,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    private yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
    readonly location: Location,
  ) {
    title.setTitle('Edit Parameter List');
    const plistId = route.snapshot.paramMap.get('list')!;
    this.plist$ = yamcs.yamcsClient.getParameterList(yamcs.instance!, plistId);
    this.plist$.then(plist => {
      this.plist = plist;
      this.form = formBuilder.group({
        name: new UntypedFormControl(plist.name, [Validators.required]),
        description: new UntypedFormControl(plist.description),
      });
      this.formSubscription = this.form.valueChanges.subscribe(() => {
        this.dirty$.next(true);
      });
      this.patterns$.next(plist.patterns || []);
    });
  }

  showAddPatternDialog() {
    const dialogRef = this.dialog.open(SelectParameterDialogComponent, {
      width: '500px',
      data: {
        label: 'Add parameter or pattern',
        okLabel: 'ADD',
        hint: 'Either an exact parameter name, or a glob pattern with *, **, or ? wildcards.',
      },
    });
    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.patterns$.next([
          ...this.patterns$.value,
          result,
        ]);
        this.dirty$.next(true);
      }
    });
  }

  deletePattern(idx: number) {
    this.patterns$.value.splice(idx, 1);
    this.patterns$.next([...this.patterns$.value]);
    this.dirty$.next(true);
  }

  moveUp(idx: number) {
    const patterns = this.patterns$.value;
    const x = patterns[idx];
    if (idx === 0) {
      patterns[idx] = patterns[patterns.length - 1];
      patterns[patterns.length - 1] = x;
    } else {
      patterns[idx] = patterns[idx - 1];
      patterns[idx - 1] = x;
    }

    this.patterns$.next([...this.patterns$.value]);
    this.dirty$.next(true);
  }

  moveDown(idx: number) {
    const patterns = this.patterns$.value;
    const x = patterns[idx];
    if (idx === patterns.length - 1) {
      patterns[idx] = patterns[0];
      patterns[0] = x;
    } else {
      patterns[idx] = patterns[idx + 1];
      patterns[idx + 1] = x;
    }

    this.patterns$.next([...this.patterns$.value]);
    this.dirty$.next(true);
  }

  onConfirm() {
    const options: UpdateParameterListRequest = {
      name: this.form.value.name,
      description: this.form.value.description,
      patternDefinition: {
        patterns: [...this.patterns$.value],
      }
    };
    this.yamcs.yamcsClient.updateParameterList(this.yamcs.instance!, this.plist.id, options)
      .then(() => this.router.navigateByUrl(`/telemetry/parameter-lists/${this.plist.id}?c=${this.yamcs.context}`))
      .catch(err => this.messageService.showError(err));
  }

  ngOnDestroy() {
    this.formSubscription?.unsubscribe();
  }
}
