import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { UntypedFormBuilder, UntypedFormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
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
    WebappSdkModule,
  ],
  template: `
    <h2 mat-dialog-title>{{ data.action === 'approve' ? 'Approve' : 'Reject' }} Booking</h2>

    <mat-dialog-content>
      <form [formGroup]="approvalForm" class="ya-form">
        <ya-field label="Booking Details">
          <div class="booking-details">
            <div class="detail-row">
              <span class="label">Ground Station:</span>
              <span>{{ data.booking.yamcsGsName }}</span>
            </div>
            <div class="detail-row">
              <span class="label">Provider:</span>
              <span>{{ data.booking.providerName }}</span>
            </div>
            <div class="detail-row">
              <span class="label">Start Time:</span>
              <span>{{ formatDateTime(data.booking.startTime) }}</span>
            </div>
            <div class="detail-row">
              <span class="label">Purpose:</span>
              <span>{{ data.booking.purpose }}</span>
            </div>
            <div class="detail-row">
              <span class="label">Requested By:</span>
              <span>{{ data.booking.requestedBy }}</span>
            </div>
          </div>
        </ya-field>

        <ya-field [label]="data.action === 'approve' ? 'Comments (optional)' : 'Rejection Reason (required)'">
          <textarea
            formControlName="comments"
            rows="3"
            [placeholder]="data.action === 'approve' ? 'Add any comments about this approval...' : 'Please provide a reason for rejection...'"
          ></textarea>
        </ya-field>
      </form>
    </mat-dialog-content>

    <mat-dialog-actions align="end">
      <ya-button mat-dialog-close>CANCEL</ya-button>
      <ya-button
        [appearance]="data.action === 'approve' ? 'primary' : 'danger'"
        (click)="onConfirm()"
        [disabled]="approvalForm.invalid">
        {{ data.action === 'approve' ? 'APPROVE' : 'REJECT' }}
      </ya-button>
    </mat-dialog-actions>
  `,
  styles: [`
    .booking-details {
      background-color: #f8f9fa;
      padding: 12px;
      border-radius: 4px;
      border: 1px solid #e9ecef;
    }
    .detail-row {
      display: flex;
      margin-bottom: 8px;
    }
    .detail-row .label {
      font-weight: 500;
      min-width: 120px;
      color: #6c757d;
    }
  `]
})
export class ApprovalDialogComponent {
  approvalForm: UntypedFormGroup;

  constructor(
    private dialogRef: MatDialogRef<ApprovalDialogComponent>,
    private fb: UntypedFormBuilder,
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