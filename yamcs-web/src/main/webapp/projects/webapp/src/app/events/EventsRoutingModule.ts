import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { mayReadEventsGuardFn } from '../core/guards/MayReadEventsGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { EventsPage } from './EventsPage';

const routes: Routes = [
  {
    path: '',
    canActivate: [authGuardFn, attachContextGuardFn],
    canActivateChild: [authGuardChildFn],
    runGuardsAndResolvers: 'always',
    component: InstancePage,
    children: [
      {
        path: '',
        pathMatch: 'full',
        component: EventsPage,
        canActivate: [mayReadEventsGuardFn],
      },
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class EventsRoutingModule { }

export const routingComponents = [
  EventsPage,
];
