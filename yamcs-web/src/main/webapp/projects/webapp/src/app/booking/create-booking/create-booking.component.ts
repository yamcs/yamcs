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
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

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

  providerOptions: { value: number; label: string }[] = [];

  ruleTypeOptions = [
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
    private cdr: ChangeDetectorRef,
    readonly yamcs: YamcsService
  ) {
    this.bookingForm = this.fb.group({
      providerId: ['', Validators.required],
      startDateTime: ['', Validators.required],
      durationHours: [1, [Validators.required, Validators.min(0.5)]],
      purpose: ['', Validators.required],
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
        this.providerOptions = this.providers.map(p => ({
          value: p.id,
          label: `${p.name} (${p.type})`
        }));
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error loading providers:', error);
        this.providers = [];
        this.providerOptions = [];
        this.cdr.markForCheck();
      }
    });
  }

  private setupFormValidation() {
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

      const startDate = new Date(formValue.startDateTime);
      const endDate = new Date(startDate.getTime() + (formValue.durationHours * 60 * 60 * 1000));

      const bookingRequest: BookingRequest = {
        providerId: formValue.providerId,
        yamcsGsName: 'GS1', // Default ground station
        startTime: startDate.toISOString(),
        endTime: endDate.toISOString(),
        purpose: formValue.purpose,
        ruleType: formValue.ruleType,
        frequencyDays: formValue.frequencyDays || undefined,
        notes: formValue.notes || undefined
      };

      this.bookingService.createBooking(bookingRequest).subscribe({
        next: (booking) => {
          console.log('Booking created successfully:', booking);
          this.router.navigate(['/booking'], {
            queryParams: { c: this.yamcs.context }
          });
        },
        error: (error) => {
          console.error('Error creating booking:', error);
          this.isSubmitting = false;
        }
      });
    }
  }

  onCancel() {
    this.router.navigate(['/booking'], {
      queryParams: { c: this.yamcs.context }
    });
  }

  showFrequencyField(): boolean {
    return this.bookingForm.get('ruleType')?.value !== 'one_time';
  }
}