import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { BookingPageComponent } from './booking-page/booking-page.component';
import { BookingListComponent } from './booking-list/booking-list.component';
import { CreateBookingComponent } from './create-booking/create-booking.component';
import { PendingApprovalsComponent } from './pending-approvals/pending-approvals.component';

export const ROUTES: Routes = [
  {
    path: '',
    canActivate: [attachContextGuardFn],
    canActivateChild: [attachContextGuardFn],
    component: InstancePageComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: BookingPageComponent,
      },
      {
        path: 'bookings',
        component: BookingListComponent,
      },
      {
        path: 'create',
        component: CreateBookingComponent,
      },
      {
        path: 'approvals',
        component: PendingApprovalsComponent,
      },
    ],
  },
];