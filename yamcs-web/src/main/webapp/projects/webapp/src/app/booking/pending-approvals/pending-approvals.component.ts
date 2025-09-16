import { ChangeDetectionStrategy, ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { BookingService, GSBooking } from '../booking.service';
import { WebappSdkModule, YamcsService } from '@yamcs/webapp-sdk';
import { ApprovalDialogComponent } from './approval-dialog.component';

@Component({
  standalone: true,
  imports: [WebappSdkModule, MatDialogModule, ApprovalDialogComponent],
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
    private cdr: ChangeDetectorRef,
    readonly yamcs: YamcsService,
    private dialog: MatDialog
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
    console.log('Approve booking clicked:', booking);
    const dialogRef = this.dialog.open(ApprovalDialogComponent, {
      width: '500px',
      data: {
        booking: booking,
        action: 'approve'
      }
    });
    console.log('Dialog opened:', dialogRef);

    dialogRef.afterClosed().subscribe(result => {
      console.log('Dialog closed with result:', result);
      if (result) {
        this.bookingService.approveBooking(booking.id, result.comments || 'Approved').subscribe({
          next: () => {
            console.log('Booking approved successfully');
            this.loadPendingBookings();
          },
          error: (error) => console.error('Error approving booking:', error)
        });
      }
    });
  }

  rejectBooking(booking: GSBooking) {
    console.log('Reject booking clicked:', booking);
    const dialogRef = this.dialog.open(ApprovalDialogComponent, {
      width: '500px',
      data: {
        booking: booking,
        action: 'reject'
      }
    });
    console.log('Dialog opened:', dialogRef);

    dialogRef.afterClosed().subscribe(result => {
      console.log('Dialog closed with result:', result);
      if (result && result.comments) {
        this.bookingService.rejectBooking(booking.id, result.comments).subscribe({
          next: () => {
            console.log('Booking rejected successfully');
            this.loadPendingBookings();
          },
          error: (error) => console.error('Error rejecting booking:', error)
        });
      }
    });
  }
}