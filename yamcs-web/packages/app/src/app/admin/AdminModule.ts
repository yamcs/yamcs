import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { AdminHomeRoutingModule, routingComponents } from './AdminHomeRoutingModule';
import { AdminPage } from './AdminPage';
import { AdminPageTemplate } from './AdminPageTemplate';
import { AdminToolbar } from './AdminToolbar';

@NgModule({
  imports: [
    SharedModule,
    AdminHomeRoutingModule,
  ],
  declarations: [
    routingComponents,
    AdminPage,
    AdminPageTemplate,
    AdminToolbar,
  ],
})
export class AdminModule {
}
