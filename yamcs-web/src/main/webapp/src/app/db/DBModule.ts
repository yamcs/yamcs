import { NgModule } from '@angular/core';
import { AdminSharedModule } from '../admin/shared/AdminSharedModule';
import { SharedModule } from '../shared/SharedModule';
import { StreamDataComponent } from './database/stream/StreamDataComponent';
import { RecordComponent } from './database/table/RecordComponent';
import { ShowEnumDialog } from './database/table/ShowEnumDialog';
import { DBRoutingModule, routingComponents } from './DBRoutingModule';
import { ColumnValuePipe } from './pipes/ColumnValuePipe';

const pipes = [
  ColumnValuePipe,
];

@NgModule({
  imports: [
    SharedModule,
    AdminSharedModule,
    DBRoutingModule,
  ],
  declarations: [
    pipes,
    routingComponents,
    RecordComponent,
    ShowEnumDialog,
    StreamDataComponent,
  ],
})
export class DBModule {
}
