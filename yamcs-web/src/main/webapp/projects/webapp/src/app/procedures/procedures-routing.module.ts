import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { RunScriptComponent } from './run-script/run-script.component';

const routes: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePage,
  children: [{
    path: 'script',
    pathMatch: 'full',
    component: RunScriptComponent,
  }]
}];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule],
})
export class ProceduresRoutingModule { }
