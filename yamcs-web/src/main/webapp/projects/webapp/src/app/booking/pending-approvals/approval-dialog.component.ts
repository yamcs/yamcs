import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { GSBooking } from '../booking.service';

interface DialogData {
  booking: GSBooking;
  action: 'approve' | 'reject';
}

@Component({
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatIconModule,
  ],
  template: `
    <h2 mat-dialog-title style="display: flex; align-items: center; gap: 8px; margin-bottom: 20px;">
      <mat-icon [color]="data.action === 'approve' ? 'primary' : 'warn'">
        {{ data.action === 'approve' ? 'check_circle' : 'cancel' }}
      </mat-icon>
      {{ data.action === 'approve' ? 'Approve' : 'Reject' }} Booking
    </h2>

    <div mat-dialog-content>
      <div style="margin-bottom: 20px; padding: 16px; background-color: #f5f5f5; border-radius: 4px;">
        <h4 style="margin: 0 0 12px 0; color: #333;">Booking Details</h4>
        <div style="display: flex; margin-bottom: 8px; align-items: flex-start;">
          <span style="font-weight: 500; min-width: 120px; color: #666;">Ground Station:</span>
          <span>{{ data.booking.yamcsGsName }}</span>
        </div>
        <div style="display: flex; margin-bottom: 8px; align-items: flex-start;">
          <span style="font-weight: 500; min-width: 120px; color: #666;">Provider:</span>
          <span>{{ data.booking.providerName }}</span>
        </div>
        <div style="display: flex; margin-bottom: 8px; align-items: flex-start;">
          <span style="font-weight: 500; min-width: 120px; color: #666;">Start Time:</span>
          <span>{{ formatDateTime(data.booking.startTime) }}</span>
        </div>
        <div style="display: flex; margin-bottom: 8px; align-items: flex-start;">
          <span style="font-weight: 500; min-width: 120px; color: #666;">End Time:</span>
          <span>{{ formatDateTime(data.booking.endTime) }}</span>
        </div>
        <div style="display: flex; margin-bottom: 8px; align-items: flex-start;">
          <span style="font-weight: 500; min-width: 120px; color: #666;">Purpose:</span>
          <span>{{ data.booking.purpose }}</span>
        </div>
        <div style="display: flex; margin-bottom: 8px; align-items: flex-start;" *ngIf="data.booking.missionName">
          <span style="font-weight: 500; min-width: 120px; color: #666;">Mission:</span>
          <span>{{ data.booking.missionName }}</span>
        </div>
        <div style="display: flex; margin-bottom: 8px; align-items: flex-start;">
          <span style="font-weight: 500; min-width: 120px; color: #666;">Requested By:</span>
          <span>{{ data.booking.requestedBy }}</span>
        </div>
      </div>

      <form [formGroup]="approvalForm" style="margin-top: 16px;">
        <mat-form-field style="width: 100%;">
          <mat-label>
            {{ data.action === 'approve' ? 'Comments (optional)' : 'Rejection Reason (required)' }}
          </mat-label>
          <textarea
            matInput
            formControlName="comments"
            rows="3"
            [placeholder]="data.action === 'approve' ? 'Add any comments about this approval...' : 'Please provide a reason for rejection...'"
          ></textarea>
          <mat-error *ngIf="approvalForm.get('comments')?.hasError('required')">
            Rejection reason is required
          </mat-error>
        </mat-form-field>
      </form>
    </div>

    <div mat-dialog-actions style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px;">
      <button mat-button (click)="onCancel()" style="min-width: 100px;">Cancel</button>
      <button
        mat-raised-button
        [color]="data.action === 'approve' ? 'primary' : 'warn'"
        (click)="onConfirm()"
        [disabled]="approvalForm.invalid"
        style="min-width: 100px;"
      >
        <mat-icon>{{ data.action === 'approve' ? 'check' : 'close' }}</mat-icon>
        {{ data.action === 'approve' ? 'Approve' : 'Reject' }}
      </button>
    </div>
  `,
  styles: []
})
export class ApprovalDialogComponent {
  approvalForm: FormGroup;

  constructor(
    private fb: FormBuilder,
    public dialogRef: MatDialogRef<ApprovalDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: DialogData
  ) {
    this.approvalForm = this.fb.group({
      comments: ['', data.action === 'reject' ? Validators.required : []]
    });
  }

  formatDateTime(dateTime: string): string {
    return new Date(dateTime).toLocaleString();
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onConfirm(): void {
    if (this.approvalForm.valid) {
      this.dialogRef.close({
        comments: this.approvalForm.get('comments')?.value
      });
    }
  }
}