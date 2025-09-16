import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { BookingService, GSBooking } from '../booking.service';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';

@Component({
  standalone: true,
  imports: [
    CommonModule,
    RouterModule,
    MatTableModule,
    MatButtonModule,
    MatIconModule,
    MatChipsModule,
    MatCardModule,
    WebappSdkModule,
  ],
  templateUrl: './booking-list.component.html',
  styleUrl: './booking-list.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BookingListComponent implements OnInit {
  bookings: GSBooking[] = [];
  displayedColumns: string[] = [
    'startTime',
    'yamcsGsName',
    'providerName',
    'purpose',
    'requestedBy',
    'status',
    'actions'
  ];

  constructor(
    private bookingService: BookingService,
    private cdr: ChangeDetectorRef,
    readonly yamcs: YamcsService
  ) {}

  ngOnInit() {
    this.loadBookings();
  }

  private loadBookings() {
    this.bookingService.getBookings().subscribe({
      next: (bookings) => {
        this.bookings = bookings || [];
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error loading bookings:', error);
        this.bookings = [];
        this.cdr.markForCheck();
      }
    });
  }

  refresh() {
    this.loadBookings();
  }

  getStatusClass(status: string): string {
    return `status-${status}`;
  }

  formatDateTime(dateTime: string): string {
    return new Date(dateTime).toLocaleString();
  }

  formatDuration(startTime: string, endTime: string): string {
    const start = new Date(startTime);
    const end = new Date(endTime);
    const durationMs = end.getTime() - start.getTime();
    const durationMinutes = Math.floor(durationMs / (1000 * 60));

    if (durationMinutes < 60) {
      return `${durationMinutes}m`;
    } else {
      const hours = Math.floor(durationMinutes / 60);
      const minutes = durationMinutes % 60;
      return `${hours}h ${minutes}m`;
    }
  }
}