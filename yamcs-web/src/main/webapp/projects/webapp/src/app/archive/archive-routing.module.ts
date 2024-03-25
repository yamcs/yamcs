import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { attachContextGuardFn } from '../core/guards/AttachContextGuard';
import { authGuardChildFn, authGuardFn } from '../core/guards/AuthGuard';
import { InstancePage } from '../shared/template/InstancePage';
import { ArchiveBrowserComponent } from './archive-browser/archive-browser.component';

const routes: Routes = [{
  path: '',
  canActivate: [authGuardFn, attachContextGuardFn],
  canActivateChild: [authGuardChildFn],
  runGuardsAndResolvers: 'always',
  component: InstancePage,
  children: [{
    path: '',
    pathMatch: 'full',
    component: ArchiveBrowserComponent,
  }]
}];

@NgModule({
  imports: [
    RouterModule.forChild(routes),
  ],
})
export class ArchiveRoutingModule {
}
