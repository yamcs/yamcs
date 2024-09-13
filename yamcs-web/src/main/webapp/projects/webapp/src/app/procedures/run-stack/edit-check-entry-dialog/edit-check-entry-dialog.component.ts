import { ChangeDetectionStrategy, Component, Inject } from '@angular/core';
import { FormArray, FormControl, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { AppMarkdownInput } from '../../../shared/markdown-input/markdown-input.component';
import { AppParameterInput } from '../../../shared/parameter-input/parameter-input.component';
import { StackedCheckEntry } from '../stack-file/StackedEntry';

@Component({
  standalone: true,
  selector: 'app-edit-check-entry-dialog',
  templateUrl: './edit-check-entry-dialog.component.html',
  styleUrl: './edit-check-entry-dialog.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AppMarkdownInput,
    AppParameterInput,
    WebappSdkModule,
  ],
})
export class EditCheckEntryDialogComponent {

  form: FormGroup;

  constructor(
    private dialogRef: MatDialogRef<EditCheckEntryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) readonly data: { edit: boolean, entry?: StackedCheckEntry; },
  ) {
    this.form = new FormGroup({
      parameters: new FormArray([]),
      comment: new FormControl(''),
    });

    if (data.entry) {
      const parameters = (data.entry.parameters || []).map(c => c.parameter);
      for (const check of parameters) {
        this.addParameterControl();
      }

      this.form.setValue({
        'parameters': parameters || [],
        'comment': data.entry.comment || '',
      });
    }

    // At least one blank line
    if (!this.parameterFormArray.length) {
      this.addParameterControl();
    }
  }

  get parameterFormArray() {
    return this.form.controls.parameters as FormArray;
  }

  get parameterControls() {
    return this.parameterFormArray.controls;
  }

  addParameterControl() {
    this.parameterFormArray.push(new FormControl(''));
  }

  removeParameterControl(idx: number) {
    this.parameterFormArray.removeAt(idx);
  }

  moveParameterControlDown(idx: number) {
    const control = this.parameterFormArray.controls[idx];
    this.parameterFormArray.removeAt(idx);
    this.parameterFormArray.insert(idx + 1, control);
  }

  moveParameterControlUp(idx: number) {
    const control = this.parameterFormArray.controls[idx];
    this.parameterFormArray.removeAt(idx);
    this.parameterFormArray.insert(idx - 1, control);
  }

  save() {
    const { value } = this.form;
    const result = {
      comment: value.comment,
      parameters: value.parameters.map((p: string) => ({ parameter: p })),
    };
    this.dialogRef.close(result);
  }
}
