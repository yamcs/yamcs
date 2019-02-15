import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { ColumnValuePipe } from './pipes/ColumnValuePipe';
import { routingComponents, SystemRoutingModule } from './SystemRoutingModule';
import { RecordComponent } from './table/RecordComponent';
import { ShowEnumDialog } from './table/ShowEnumDialog';

const dialogComponents = [
  ShowEnumDialog,
];

const pipes = [
  ColumnValuePipe,
];

@NgModule({
  imports: [
    SharedModule,
    SystemRoutingModule,
  ],
  declarations: [
    routingComponents,
    dialogComponents,
    pipes,
    RecordComponent,
  ],
  entryComponents: [
    dialogComponents,
  ]
})
export class SystemModule {
}
