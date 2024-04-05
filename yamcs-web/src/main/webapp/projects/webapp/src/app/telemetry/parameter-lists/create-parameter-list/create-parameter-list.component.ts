import { Location } from '@angular/common';
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { UntypedFormBuilder, UntypedFormControl, UntypedFormGroup, Validators } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { CreateParameterListRequest, MessageService, WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { InstancePageTemplateComponent } from '../../../shared/instance-page-template/instance-page-template.component';
import { InstanceToolbarComponent } from '../../../shared/instance-toolbar/instance-toolbar.component';
import { SelectParameterDialogComponent } from '../../../shared/select-parameter-dialog/select-parameter-dialog.component';

@Component({
  standalone: true,
  templateUrl: './create-parameter-list.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    InstanceToolbarComponent,
    InstancePageTemplateComponent,
    WebappSdkModule,
  ],
})
export class CreateParameterListComponent {

  form: UntypedFormGroup;

  patterns$ = new BehaviorSubject<string[]>([]);

  constructor(
    formBuilder: UntypedFormBuilder,
    title: Title,
    private router: Router,
    private route: ActivatedRoute,
    readonly yamcs: YamcsService,
    private dialog: MatDialog,
    private messageService: MessageService,
    readonly location: Location,
  ) {
    title.setTitle('Create a Parameter List');
    this.form = formBuilder.group({
      name: new UntypedFormControl('', [Validators.required]),
      description: new UntypedFormControl(),
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
      }
    });
  }

  deletePattern(idx: number) {
    this.patterns$.value.splice(idx, 1);
    this.patterns$.next([...this.patterns$.value]);
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
  }

  onConfirm() {
    const options: CreateParameterListRequest = {
      name: this.form.value.name,
      description: this.form.value.description,
      patterns: [...this.patterns$.value],
    };
    this.yamcs.yamcsClient.createParameterList(this.yamcs.instance!, options)
      .then(() => this.router.navigate(['..'], {
        relativeTo: this.route,
        queryParams: { c: this.yamcs.context },
      }))
      .catch(err => this.messageService.showError(err));
  }
}
