import { Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePageComponent } from '../shared/instance-page/instance-page.component';
import { ActionLogTabComponent } from './action-log-tab/action-log-tab.component';
import { LinkListComponent } from './link-list/link-list.component';
import { LinkComponent } from './link/link.component';

export const ROUTES: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePageComponent,
  children: [{
    path: '',
    pathMatch: 'full',
    component: LinkListComponent,
  }, {
    path: 'log',
    component: ActionLogTabComponent,
  }, {
    path: ':link',
    component: LinkComponent,
  }]
}];
