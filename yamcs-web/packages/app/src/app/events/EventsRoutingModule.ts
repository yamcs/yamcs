import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from '../core/guards/AuthGuard';
import { InstanceExistsGuard } from '../core/guards/InstanceExistsGuard';
import { MayReadEventsGuard } from '../core/guards/MayReadEventsGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { EventsPage } from './EventsPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [AuthGuard, InstanceExistsGuard],
    canActivateChild: [AuthGuard],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: EventsPage,
        canActivate: [MayReadEventsGuard],
      },
    ]
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class EventsRoutingModule { }

export const routingComponents = [
  EventsPage,
];
