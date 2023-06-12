import { NgModule } from '@angular/core';
import { SharedModule } from '../../shared/SharedModule';
import { AdminPage } from './AdminPage';
import { AdminPageTemplate } from './AdminPageTemplate';
import { AdminToolbar } from './AdminToolbar';

const sharedComponents = [
  AdminPage,
  AdminPageTemplate,
  AdminToolbar,
];

@NgModule({
  imports: [
    SharedModule,
  ],
  declarations: [
    sharedComponents,
  ],
  exports: [
    sharedComponents,
  ]
})
export class AdminSharedModule {
}
