import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';

export interface GSProvider {
  id: number;
  name: string;
  type: 'leafspace' | 'dhruva' | 'isro';
  contactEmail?: string;
  contactPhone?: string;
  apiEndpoint?: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface GSBooking {
  id: number;
  providerId: number;
  yamcsGsName: string;
  startTime: string;
  endTime: string;
  purpose: string;
  missionName?: string;
  satelliteName?: string;
  ruleType: 'daily' | 'weekly' | 'monthly' | 'one_time';
  frequencyDays?: number;
  status: 'pending' | 'approved' | 'rejected' | 'cancelled' | 'completed';
  requestedBy: string;
  approvedBy?: string;
  approvedAt?: string;
  rejectionReason?: string;
  notes?: string;
  createdAt: string;
  updatedAt: string;
  providerName?: string;
  providerType?: string;
  durationMinutes?: number;
}

export interface BookingRequest {
  providerId: number;
  yamcsGsName: string;
  startTime: string;
  endTime: string;
  purpose: string;
  missionName?: string;
  satelliteName?: string;
  ruleType: 'daily' | 'weekly' | 'monthly' | 'one_time';
  frequencyDays?: number;
  notes?: string;
}

export interface ApprovalRequest {
  comments?: string;
}

@Injectable({
  providedIn: 'root'
})
export class BookingService {

  constructor(private http: HttpClient) {}

  // Provider methods
  getProviders(): Observable<GSProvider[]> {
    return this.http.get<{providers: GSProvider[]}>('/api/booking/providers')
      .pipe(map(response => response.providers));
  }

  // Booking methods
  getBookings(): Observable<GSBooking[]> {
    return this.http.get<{bookings: GSBooking[]}>('/api/booking/bookings')
      .pipe(map(response => response.bookings));
  }

  getPendingBookings(): Observable<GSBooking[]> {
    return this.http.get<{bookings: GSBooking[]}>('/api/booking/bookings/pending')
      .pipe(map(response => response.bookings));
  }

  createBooking(booking: BookingRequest): Observable<GSBooking> {
    return this.http.post<GSBooking>('/api/booking/bookings', booking);
  }

  approveBooking(bookingId: number, comments?: string): Observable<{status: string}> {
    const request: ApprovalRequest = { comments };
    return this.http.post<{status: string}>(`/api/booking/bookings/${bookingId}/approve`, request);
  }

  rejectBooking(bookingId: number, reason: string): Observable<{status: string}> {
    const request: ApprovalRequest = { comments: reason };
    return this.http.post<{status: string}>(`/api/booking/bookings/${bookingId}/reject`, request);
  }
}