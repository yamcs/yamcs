import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { BookingService, GSProvider, BookingRequest } from '../booking.service';
import { WebappSdkModule, YamcsService, YaSelectOption, utils } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  imports: [WebappSdkModule],
  templateUrl: './create-booking.component.html',
  styleUrl: './create-booking.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CreateBookingComponent implements OnInit {
  form: UntypedFormGroup;
  providers: GSProvider[] = [];

  providerOptions: YaSelectOption[] = [];

  ruleTypeOptions: YaSelectOption[] = [
    { id: 'one_time', label: 'One-time booking' },
    { id: 'daily', label: 'Daily recurring' },
    { id: 'weekly', label: 'Weekly recurring' },
    { id: 'monthly', label: 'Monthly recurring' }
  ];

  isSubmitting = false;
  submissionStatus: 'idle' | 'success' | 'error' = 'idle';
  submissionMessage = '';

  constructor(
    private formBuilder: UntypedFormBuilder,
    private bookingService: BookingService,
    private router: Router,
    private cdr: ChangeDetectorRef,
    readonly yamcs: YamcsService
  ) {
    this.form = formBuilder.group({
      providerId: ['', Validators.required],
      startDateTime: [utils.toISOString(yamcs.getMissionTime()), Validators.required],
      purpose: ['', Validators.required],
      ruleType: 'one_time',
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
          id: p.id.toString(),
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
    this.form.get('ruleType')?.valueChanges.subscribe((ruleType) => {
      const frequencyControl = this.form.get('frequencyDays');
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
    if (this.form.valid && !this.isSubmitting) {
      this.isSubmitting = true;

      const formValue = this.form.value;
      const startDate = new Date(formValue.startDateTime);
      const endDate = new Date(startDate.getTime() + (2 * 60 * 60 * 1000)); // Default 2 hours

      const bookingRequest: BookingRequest = {
        providerId: parseInt(formValue.providerId),
        yamcsGsName: 'GS1', // Default ground station
        startTime: utils.toISOString(formValue.startDateTime),
        endTime: endDate.toISOString(),
        purpose: formValue.purpose,
        ruleType: formValue.ruleType,
        frequencyDays: formValue.frequencyDays || undefined,
        notes: formValue.notes || undefined
      };

      this.bookingService.createBooking(bookingRequest).subscribe({
        next: (booking) => {
          console.log('Booking created successfully:', booking);
          this.isSubmitting = false;
          this.submissionStatus = 'success';
          this.submissionMessage = 'Booking created successfully!';
          this.cdr.markForCheck();

          // Navigate after showing success message
          setTimeout(() => {
            this.router.navigate(['/booking'], {
              queryParams: { c: this.yamcs.context }
            });
          }, 2000);
        },
        error: (error) => {
          console.error('Error creating booking:', error);
          this.isSubmitting = false;
          this.submissionStatus = 'error';
          this.submissionMessage = 'Failed to create booking. Please try again.';
          this.cdr.markForCheck();
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
    return this.form.get('ruleType')?.value !== 'one_time';
  }

  getFrequencyLabel(): string {
    const ruleType = this.form.get('ruleType')?.value;
    switch (ruleType) {
      case 'daily':
        return 'Frequency (days)';
      case 'weekly':
        return 'Frequency (weeks)';
      case 'monthly':
        return 'Frequency (months)';
      default:
        return 'Frequency (days)';
    }
  }

  getFrequencyNote(): string {
    const ruleType = this.form.get('ruleType')?.value;
    switch (ruleType) {
      case 'daily':
        return 'Number of days between recurring bookings';
      case 'weekly':
        return 'Number of weeks between recurring bookings';
      case 'monthly':
        return 'Number of months between recurring bookings';
      default:
        return 'Number of days between recurring bookings';
    }
  }
}