import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { DisplaysPageComponent } from './pages/displays.component';
import { DisplayPageComponent } from './pages/display.component';

const routes = [
  {
    path: '',
    component: DisplaysPageComponent,
    pathMatch: 'full',
  }, {
    path: ':name',
    component: DisplayPageComponent,
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class DisplaysRoutingModule { }

export const routingComponents = [
  DisplaysPageComponent,
  DisplayPageComponent,
];
