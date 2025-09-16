import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { BookingService, GSProvider, GSBooking } from '../booking.service';
import { WebappSdkModule } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    WebappSdkModule,
  ],
  templateUrl: './booking-page.component.html',
  styleUrl: './booking-page.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookingPageComponent implements OnInit {
  providers: GSProvider[] = [];
  recentBookings: GSBooking[] = [];

  pendingCount = 0;
  activeCount = 0;
  providerCount = 0;

  displayedColumns = ['startTime', 'yamcsGsName', 'providerName', 'missionName', 'status'];

  constructor(
    private bookingService: BookingService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.loadData();
  }

  private loadData() {
    // Load providers
    this.bookingService.getProviders().subscribe({
      next: (providers) => {
        this.providers = providers || [];
        this.providerCount = (providers || []).length;
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error loading providers:', error);
        this.providers = [];
        this.providerCount = 0;
        this.cdr.markForCheck();
      }
    });

    // Load pending bookings count
    this.bookingService.getPendingBookings().subscribe({
      next: (bookings) => {
        this.pendingCount = (bookings || []).length;
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error loading pending bookings:', error);
        this.pendingCount = 0;
        this.cdr.markForCheck();
      }
    });

    // Load recent bookings
    this.bookingService.getBookings().subscribe({
      next: (bookings) => {
        const safeBookings = bookings || [];
        // Get recent bookings (last 10)
        this.recentBookings = safeBookings.slice(0, 10);

        // Count active bookings (approved or pending)
        this.activeCount = safeBookings.filter(b =>
          b.status === 'approved' || b.status === 'pending'
        ).length;

        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error loading bookings:', error);
        this.recentBookings = [];
        this.activeCount = 0;
        this.cdr.markForCheck();
      }
    });
  }
}