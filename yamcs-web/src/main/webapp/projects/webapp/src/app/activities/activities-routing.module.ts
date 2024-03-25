import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { ActivityDetailsTabComponent } from './activity-details-tab/activity-details-tab.component';
import { ActivityListComponent } from './activity-list/activity-list.component';
import { ActivityLogTabComponent } from './activity-log-tab/activity-log-tab.component';
import { ActivityComponent } from './activity/activity.component';

const routes: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePage,
  children: [{
    path: '',
    pathMatch: 'full',
    component: ActivityListComponent,
  }, {
    path: ':activityId',
    component: ActivityComponent,
    children: [{
      path: '',
      pathMatch: 'full',
      redirectTo: 'log',
    }, {
      path: 'log',
      component: ActivityLogTabComponent,
    }, {
      path: 'details',
      component: ActivityDetailsTabComponent,
    }]
  }]
}];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ActivitiesRoutingModule { }
