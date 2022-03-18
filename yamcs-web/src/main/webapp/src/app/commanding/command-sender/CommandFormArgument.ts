import { AfterViewInit, ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { BehaviorSubject } from 'rxjs';
import { Argument, ArgumentMember, EnumValue } from '../../client';
import { Option } from '../../shared/forms/Select';
import { SelectEnumerationDialog } from './SelectEnumerationDialog';

@Component({
  selector: 'app-command-form-argument',
  templateUrl: './CommandFormArgument.html',
  styleUrls: ['./CommandFormArgument.css'],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CommandFormArgument implements OnInit, AfterViewInit {

  @Input()
  formGroup: FormGroup;

  @Input()
  argument: Argument | ArgumentMember;

  @Input()
  parent: string;

  controlName$ = new BehaviorSubject<string | null>(null);
  hexToggle$ = new BehaviorSubject<boolean>(false);

  selectOptions$ = new BehaviorSubject<Option[]>([]);

  constructor(private dialog: MatDialog) {
  }

  ngOnInit() {
    if (this.parent) {
      this.controlName$.next(this.parent + '.' + this.argument.name);
    } else {
      this.controlName$.next(this.argument.name);
    }
  }

  ngAfterViewInit() {
    if (this.argument?.type?.engType === 'enumeration') {
      const selectOptions = [];
      for (const enumValue of this.argument.type.enumValue || []) {
        selectOptions.push({
          id: enumValue.label,
          label: enumValue.label,
        });
      }
      this.selectOptions$.next(selectOptions);
    }
  }

  openSelectEnumerationDialog() {
    this.dialog.open(SelectEnumerationDialog, {
      width: '600px',
      data: { argument: this.argument },
      panelClass: ['no-padding-dialog'],
    }).afterClosed().subscribe((result: EnumValue) => {
      if (result) {
        const control = this.formGroup.controls[this.controlName$.value!];
        control.setValue(result.label);
      }
    });
  }
}
