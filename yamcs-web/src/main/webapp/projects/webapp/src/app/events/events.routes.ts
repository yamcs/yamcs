import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { mayReadEventsGuardFn } from '../core/guards/MayReadEventsGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { EventListComponent } from './event-list/event-list.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    component: EventListComponent,
    canActivate: [mayReadEventsGuardFn],
  }]
}];
