import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { routingComponents, SystemRoutingModule } from './SystemRoutingModule';
import { RecordComponent } from './table/RecordComponent';
import { ShowEnumDialog } from './table/ShowEnumDialog';

const dialogComponents = [
  ShowEnumDialog,
];

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    RecordComponent,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class SystemModule {
}
