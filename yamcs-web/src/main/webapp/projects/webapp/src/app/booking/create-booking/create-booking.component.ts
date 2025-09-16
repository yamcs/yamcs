import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { BookingService, GSProvider, BookingRequest } from '../booking.service';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    ReactiveFormsModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatButtonModule,
    MatIconModule,
    MatDatepickerModule,
    MatNativeDateModule,
    WebappSdkModule,
  ],
  templateUrl: './create-booking.component.html',
  styleUrl: './create-booking.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateBookingComponent implements OnInit {
  bookingForm: FormGroup;
  providers: GSProvider[] = [];
  ruleTypes = [
    { value: 'one_time', label: 'One-time booking' },
    { value: 'daily', label: 'Daily recurring' },
    { value: 'weekly', label: 'Weekly recurring' },
    { value: 'monthly', label: 'Monthly recurring' }
  ];

  isSubmitting = false;

  constructor(
    private fb: FormBuilder,
    private bookingService: BookingService,
    private router: Router,
    private cdr: ChangeDetectorRef
  ) {
    this.bookingForm = this.fb.group({
      providerId: ['', Validators.required],
      yamcsGsName: ['', Validators.required],
      startDate: ['', Validators.required],
      startTime: ['', Validators.required],
      endDate: ['', Validators.required],
      endTime: ['', Validators.required],
      purpose: ['', Validators.required],
      missionName: [''],
      satelliteName: [''],
      ruleType: ['one_time', Validators.required],
      frequencyDays: [''],
      notes: ['']
    });
  }

  ngOnInit() {
    this.loadProviders();
    this.setupFormValidation();
  }

  private loadProviders() {
    this.bookingService.getProviders().subscribe({
      next: (providers) => {
        this.providers = providers || [];
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error loading providers:', error);
        this.providers = [];
        this.cdr.markForCheck();
      }
    });
  }

  private setupFormValidation() {
    // Add custom validation for end time being after start time
    this.bookingForm.valueChanges.subscribe(() => {
      const startDate = this.bookingForm.get('startDate')?.value;
      const startTime = this.bookingForm.get('startTime')?.value;
      const endDate = this.bookingForm.get('endDate')?.value;
      const endTime = this.bookingForm.get('endTime')?.value;

      if (startDate && startTime && endDate && endTime) {
        const start = this.combineDateTime(startDate, startTime);
        const end = this.combineDateTime(endDate, endTime);

        if (start >= end) {
          this.bookingForm.get('endTime')?.setErrors({ invalidEndTime: true });
        } else {
          const endTimeControl = this.bookingForm.get('endTime');
          if (endTimeControl?.hasError('invalidEndTime')) {
            endTimeControl.setErrors(null);
          }
        }
      }
    });

    // Show/hide frequency field based on rule type
    this.bookingForm.get('ruleType')?.valueChanges.subscribe((ruleType) => {
      const frequencyControl = this.bookingForm.get('frequencyDays');
      if (ruleType === 'one_time') {
        frequencyControl?.clearValidators();
        frequencyControl?.setValue('');
      } else {
        frequencyControl?.setValidators([Validators.required, Validators.min(1)]);
      }
      frequencyControl?.updateValueAndValidity();
    });
  }

  private combineDateTime(date: Date, time: string): Date {
    const [hours, minutes] = time.split(':').map(Number);
    const combined = new Date(date);
    combined.setHours(hours, minutes, 0, 0);
    return combined;
  }

  private formatDateTime(date: Date, time: string): string {
    const combined = this.combineDateTime(date, time);
    return combined.toISOString();
  }

  onSubmit() {
    if (this.bookingForm.valid && !this.isSubmitting) {
      this.isSubmitting = true;

      const formValue = this.bookingForm.value;

      const bookingRequest: BookingRequest = {
        providerId: formValue.providerId,
        yamcsGsName: formValue.yamcsGsName,
        startTime: this.formatDateTime(formValue.startDate, formValue.startTime),
        endTime: this.formatDateTime(formValue.endDate, formValue.endTime),
        purpose: formValue.purpose,
        missionName: formValue.missionName || undefined,
        satelliteName: formValue.satelliteName || undefined,
        ruleType: formValue.ruleType,
        frequencyDays: formValue.frequencyDays || undefined,
        notes: formValue.notes || undefined
      };

      this.bookingService.createBooking(bookingRequest).subscribe({
        next: (booking) => {
          console.log('Booking created successfully:', booking);
          this.router.navigate(['/booking']);
        },
        error: (error) => {
          console.error('Error creating booking:', error);
          this.isSubmitting = false;
        }
      });
    }
  }

  onCancel() {
    this.router.navigate(['/booking']);
  }

  getRuleTypeLabel(value: string): string {
    return this.ruleTypes.find(type => type.value === value)?.label || value;
  }

  showFrequencyField(): boolean {
    return this.bookingForm.get('ruleType')?.value !== 'one_time';
  }
}