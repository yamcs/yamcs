import { NgModule } from '@angular/core';
import { SharedModule } from '../shared/SharedModule';
import { CommandingRoutingModule, routingComponents } from './CommandingRoutingModule';
import { ChangeLevelDialog } from './clearances/ChangeLevelDialog';
import { ClearancesPageTabs } from './clearances/ClearancesPageTabs';
import { AcknowledgmentIcon } from './command-history/AcknowledgmentIcon';
import { CascadingPrefixPipe } from './command-history/CascadingPrefixPipe';
import { CommandArguments } from './command-history/CommandArguments';
import { CommandDetail } from './command-history/CommandDetail';
import { CommandHistoryPrintable } from './command-history/CommandHistoryPrintable';
import { ExtraAcknowledgmentsTable } from './command-history/ExtraAcknowledgmentsTable';
import { TransmissionConstraintsIcon } from './command-history/TransmissionConstraintsIcon';
import { YamcsAcknowledgmentsTable } from './command-history/YamcsAcknowledgmentsTable';
import { CommandForm } from './command-sender/CommandForm';

import { ScheduleCommandDialog } from './command-sender/ScheduleCommandDialog';
import { SelectEnumerationDialog } from './command-sender/SelectEnumerationDialog';
import { SendCommandWizardStep } from './command-sender/SendCommandWizardStep';
import { AggregateArgument } from './command-sender/arguments/aggregate/AggregateArgument';
import { ArgumentComponent } from './command-sender/arguments/argument/ArgumentComponent';
import { ArrayArgument } from './command-sender/arguments/array/ArrayArgument';
import { BinaryArgument } from './command-sender/arguments/binary/BinaryArgument';
import { BooleanArgument } from './command-sender/arguments/boolean/BooleanArgument';
import { EnumerationArgument } from './command-sender/arguments/enumeration/EnumerationArgument';
import { FloatArgument } from './command-sender/arguments/float/FloatArgument';
import { IntegerArgument } from './command-sender/arguments/integer/IntegerArgument';
import { StringArgument } from './command-sender/arguments/string/StringArgument';
import { TimeArgument } from './command-sender/arguments/time/TimeArgument';
import { CommandDownloadLinkPipe } from './pipes/CommandDownloadLinkPipe';
import { QueuedCommandsTable } from './queues/QueuedCommandsTable';
import { AcknowledgmentNamePipe } from './stacks/AcknowledgmentNamePipe';
import { AdvanceAckHelp } from './stacks/AdvanceAckHelp';
import { CreateFolderDialog } from './stacks/CreateFolderDialog';
import { CreateStackDialog } from './stacks/CreateStackDialog';
import { EditStackEntryDialog } from './stacks/EditStackEntryDialog';
import { RenameStackDialog } from './stacks/RenameStackDialog';
import { ScheduleStackDialog } from './stacks/ScheduleStackDialog';
import { StackFilePageDirtyDialog } from './stacks/StackFilePageDirtyDialog';
import { StackedCommandDetail } from './stacks/StackedCommandDetail';

const pipes = [
  AcknowledgmentNamePipe,
  CascadingPrefixPipe,
  CommandDownloadLinkPipe,
];

@NgModule({
  imports: [
    SharedModule,
    CommandingRoutingModule,
  ],
  declarations: [
    routingComponents,
    pipes,
    AcknowledgmentIcon,
    AdvanceAckHelp,
    AggregateArgument,
    ArgumentComponent,
    ArrayArgument,
    BinaryArgument,
    BooleanArgument,
    CommandArguments,
    EditStackEntryDialog,
    ChangeLevelDialog,
    ClearancesPageTabs,
    CommandDetail,
    CommandForm,
    CommandHistoryPrintable,
    CreateFolderDialog,
    CreateStackDialog,
    EnumerationArgument,
    ExtraAcknowledgmentsTable,
    FloatArgument,
    IntegerArgument,
    QueuedCommandsTable,
    RenameStackDialog,
    ScheduleCommandDialog,
    ScheduleStackDialog,
    SelectEnumerationDialog,
    SendCommandWizardStep,
    StackedCommandDetail,
    StackFilePageDirtyDialog,
    StringArgument,
    TimeArgument,
    TransmissionConstraintsIcon,
    YamcsAcknowledgmentsTable,
  ],
})
export class CommandingModule {
}
