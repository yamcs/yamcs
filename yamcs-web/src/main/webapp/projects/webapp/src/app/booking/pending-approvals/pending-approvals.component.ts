import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatCardModule } from '@angular/material/card';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { BookingService, GSBooking } from '../booking.service';
import { WebappSdkModule } from '@yamcs/webapp-sdk';
import { ApprovalDialogComponent } from './approval-dialog.component';

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
    MatDialogModule,
    WebappSdkModule,
  ],
  templateUrl: './pending-approvals.component.html',
  styleUrl: './pending-approvals.component.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PendingApprovalsComponent implements OnInit {
  pendingBookings: GSBooking[] = [];
  displayedColumns: string[] = [
    'startTime',
    'yamcsGsName',
    'providerName',
    'purpose',
    'requestedBy',
    'actions'
  ];

  constructor(
    private bookingService: BookingService,
    private dialog: MatDialog,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit() {
    this.loadPendingBookings();
  }

  private loadPendingBookings() {
    this.bookingService.getPendingBookings().subscribe({
      next: (bookings) => {
        this.pendingBookings = bookings || [];
        this.cdr.markForCheck();
      },
      error: (error) => {
        console.error('Error loading pending bookings:', error);
        this.pendingBookings = [];
        this.cdr.markForCheck();
      }
    });
  }

  refresh() {
    this.loadPendingBookings();
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

  approveBooking(booking: GSBooking) {
    const dialogRef = this.dialog.open(ApprovalDialogComponent, {
      width: '400px',
      data: {
        booking: booking,
        action: 'approve'
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result) {
        this.bookingService.approveBooking(booking.id, result.comments).subscribe({
          next: () => {
            console.log('Booking approved successfully');
            this.loadPendingBookings(); // Refresh the list
          },
          error: (error) => console.error('Error approving booking:', error)
        });
      }
    });
  }

  rejectBooking(booking: GSBooking) {
    const dialogRef = this.dialog.open(ApprovalDialogComponent, {
      width: '400px',
      data: {
        booking: booking,
        action: 'reject'
      }
    });

    dialogRef.afterClosed().subscribe(result => {
      if (result && result.comments) {
        this.bookingService.rejectBooking(booking.id, result.comments).subscribe({
          next: () => {
            console.log('Booking rejected successfully');
            this.loadPendingBookings(); // Refresh the list
          },
          error: (error) => console.error('Error rejecting booking:', error)
        });
      }
    });
  }
}