import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { ActionLogTab } from './ActionLogTab';
import { LinkPage } from './LinkPage';
import { LinksPage } from './LinksPage';

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
        component: LinksPage,
      }, {
        path: 'log',
        component: ActionLogTab,
      }, {
        path: ':link',
        component: LinkPage,
      }
    ]
  }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class LinksRoutingModule { }

export const routingComponents = [
  ActionLogTab,
  LinksPage,
  LinkPage,
];
