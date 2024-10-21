import { ChangeDetectionStrategy, Component, Input, OnChanges, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { AcknowledgmentInfo, AdvancementParams, Command, WebappSdkModule, YamcsService, YaSelectOption } from '@yamcs/webapp-sdk';
import { AdvanceAckHelpComponent } from '../../../procedures/run-stack/advance-ack-help/advance-ack-help.component';
import { AppMarkdownInput } from '../../../shared/markdown-input/markdown-input.component';
import { TemplateProvider } from './TemplateProvider';

@Component({
  standalone: true,
  selector: 'app-stack-advancement-form',
  templateUrl: './stack-advancement-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    AdvanceAckHelpComponent,
    AppMarkdownInput,
    WebappSdkModule,
  ],
})
export class StackAdvancementForm implements OnInit, OnChanges {

  @Input()
  formGroup: FormGroup;

  @Input()
  command: Command;

  @Input()
  templateProvider: TemplateProvider;

  verifierAcknowledgments: AcknowledgmentInfo[] = [];
  extraAcknowledgments: AcknowledgmentInfo[] = [];
  ackOptions: YaSelectOption[] = [
    { id: '', label: 'Inherit' },
    { id: 'Acknowledge_Queued', label: 'Queued', group: true },
    { id: 'Acknowledge_Released', label: 'Released' },
    { id: 'Acknowledge_Sent', label: 'Sent' },
    { id: 'CommandComplete', label: 'Completed' },
  ];

  constructor(private yamcs: YamcsService) {
  }

  ngOnInit(): void {
    this.verifierAcknowledgments = [];

    // Order command definitions top-down
    const commandHierarchy: Command[] = [];
    let c: Command | undefined = this.command;
    while (c) {
      commandHierarchy.unshift(c);
      c = c.baseCommand;
    }
    for (const command of commandHierarchy) {
      for (const verifier of command.verifier ?? []) {
        this.verifierAcknowledgments.push({ name: `Verifier_${verifier.stage}` });
      }
    }
    let first = true;
    for (const verifier of this.verifierAcknowledgments) {
      this.ackOptions.push({
        id: verifier.name,
        label: verifier.name,
        group: first,
      });
      first = false;
    }

    this.extraAcknowledgments = this.yamcs.getProcessor()?.acknowledgments ?? [];
    first = true;
    for (const ack of this.extraAcknowledgments) {
      this.ackOptions.push({
        id: ack.name,
        label: ack.name.replace('Acknowledge_', ''),
        group: first,
      });
      first = false;
    }

    this.ackOptions.push({
      id: 'custom',
      label: 'Custom',
      group: true,
    });
  }

  ngOnChanges(): void {
    if (!this.formGroup.contains('advancement')) {
      const advancementGroup = new FormGroup({
        acknowledgment: new FormControl(''),
        ackCustom: new FormControl(''),
        wait: new FormControl(null),
      });
      this.formGroup.addControl('advancement', advancementGroup);

      advancementGroup.valueChanges.subscribe((result) => {
        if (result.acknowledgment !== 'custom') {
          advancementGroup.patchValue({ 'ackCustom': undefined }, {
            emitEvent: false,
          });
        }
      });
    }

    if (this.templateProvider) {
      this.formGroup.patchValue({
        comment: this.templateProvider.getComment() || '',
        stream: this.templateProvider.getStream() || '',
      });

      const advancement = this.templateProvider.getAdvancementParams();
      if (advancement) {
        const match = this.ackOptions.find(el => el.id === advancement.acknowledgment);
        const acknowledgment = match ? match.id : 'custom';
        const ackCustom = acknowledgment === 'custom' ? advancement.acknowledgment : '';
        let wait = advancement.wait ?? null;
        this.advancementGroup.patchValue({
          acknowledgment,
          ackCustom,
          wait,
        });
      }
    }
  }

  get advancementGroup() {
    return this.formGroup.controls['advancement'] as FormGroup;
  }

  get custom() {
    return this.advancementGroup.controls['acknowledgment'].value === 'custom';
  }

  getResult(): AdvancementParams | undefined {
    let acknowledgment = this.advancementGroup.get('acknowledgment')?.value;
    if (acknowledgment === 'custom') {
      acknowledgment = this.advancementGroup.get('ackCustom')?.value?.trim();
    }

    const wait = this.advancementGroup.get('wait')?.value ?? undefined;
    if (acknowledgment || wait !== undefined) {
      return { acknowledgment, wait };
    } else {
      return undefined;
    }
  }
}
