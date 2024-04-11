import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { GapListComponent } from './gap-list/gap-list.component';
import { GapRequestListComponent } from './gap-request-list/gap-request-list.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    component: GapListComponent,
  }, {
    path: 'requests',
    component: GapRequestListComponent,
  }]
}];
