import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule, YaStepper, YaStepperStep } from '@yamcs/webapp-sdk';
import { AppMarkdownInput } from '../../../shared/markdown-input/markdown-input.component';
import { AppParameterInput } from '../../../shared/parameter-input/parameter-input.component';
import { StackedVerifyEntry } from '../stack-file/StackedEntry';

@Component({
  standalone: true,
  selector: 'app-edit-verify-entry-dialog',
  templateUrl: './edit-verify-entry-dialog.component.html',
  styleUrl: './edit-verify-entry-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppMarkdownInput,
    AppParameterInput,
    YaStepper,
    YaStepperStep,
    WebappSdkModule,
  ],
})
export class EditVerifyEntryDialogComponent {

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<EditVerifyEntryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: { edit: boolean, entry?: StackedVerifyEntry; },
  ) {
    this.form = new FormGroup({
      condition: new FormArray([]),
      delay: new FormControl(0, [Validators.required]),
      timeout: new FormControl(''),
      comment: new FormControl(''),
    });

    if (data.entry) {
      const condition = (data.entry.condition || []);
      for (const comparison of condition) {
        this.addComparisonGroup();
      }

      this.form.setValue({
        'condition': condition,
        'delay': data.entry.delay ?? 0,
        'timeout': data.entry.timeout || '',
        'comment': data.entry.comment || '',
      });
    }

    // At least one blank line
    if (!this.conditionFormArray.length) {
      this.addComparisonGroup();
    }
  }

  get conditionFormArray() {
    return this.form.controls.condition as FormArray;
  }

  get comparisonGroups() {
    return this.conditionFormArray.controls;
  }

  addComparisonGroup() {
    this.conditionFormArray.push(new FormGroup({
      'parameter': new FormControl('', [Validators.required]),
      'operator': new FormControl('eq', [Validators.required]),
      'value': new FormControl('', [Validators.required]),
    }));
  }

  removeComparisonGroup(idx: number) {
    this.conditionFormArray.removeAt(idx);
  }

  moveComparisonGroupDown(idx: number) {
    const control = this.conditionFormArray.controls[idx];
    this.conditionFormArray.removeAt(idx);
    this.conditionFormArray.insert(idx + 1, control);
  }

  moveComparisonGroupUp(idx: number) {
    const control = this.conditionFormArray.controls[idx];
    this.conditionFormArray.removeAt(idx);
    this.conditionFormArray.insert(idx - 1, control);
  }

  save() {
    const { value } = this.form;
    const result: { [key: string]: any; } = {
      condition: value.condition,
      delay: Math.max(value.delay, 0),
      comment: value.comment,
    };
    if (value.timeout !== '' && value.timeout !== null) {
      result.timeout = Math.max(value.timeout, 0);
    }
    this.dialogRef.close(result);
  }
}
