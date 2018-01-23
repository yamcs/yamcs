import { NgModule } from '@angular/core';
import { RouterModule } from '@angular/router';

import { LinksPageComponent } from './pages/links.component';

const routes = [
  {
    path: '',
    component: LinksPageComponent,
  }
];

@NgModule({
  imports: [ RouterModule.forChild(routes) ],
  exports: [ RouterModule ],
})
export class LinksRoutingModule { }

export const routingComponents = [
  LinksPageComponent,
];
