import { ChangeDetectionStrategy, Component, Input, OnChanges, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { CommandOption, ConfigService, Value, WebappSdkModule, YaSelectOption } from '@yamcs/webapp-sdk';
import { BehaviorSubject } from 'rxjs';
import { renderValue } from '../arguments/argument/argument.component';
import { TemplateProvider } from './TemplateProvider';

@Component({
  standalone: true,
  selector: 'app-command-options-form',
  templateUrl: './command-options-form.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    WebappSdkModule,
  ],
})
export class CommandOptionsForm implements OnInit, OnChanges {

  @Input()
  formGroup: FormGroup;

  @Input()
  templateProvider: TemplateProvider;

  commandOptions: CommandOption[];

  streamOptions$ = new BehaviorSubject<YaSelectOption[]>([]);

  constructor(configService: ConfigService) {
    this.commandOptions = configService.getCommandOptions();

    const streamOptions: YaSelectOption[] = configService.getTcStreams()
      .map(streamName => ({ id: streamName, label: streamName }));
    this.streamOptions$.next(streamOptions);
  }

  ngOnInit(): void {
    this.formGroup.addControl('stream', new FormControl(''));
    for (const option of this.commandOptions) {
      this.formGroup.addControl('extra__' + option.id, new FormControl(null));
    }
  }

  ngOnChanges(): void {
    if (this.templateProvider) {
      for (const option of this.commandOptions || []) {
        const previousValue = this.templateProvider.getOption(option.id, option.type);
        if (previousValue !== undefined) {
          this.formGroup.controls['extra__' + option.id].setValue(renderValue(previousValue));
        }
      }
    }
  }

  getStream() {
    const control = this.formGroup.controls['stream'];
    return control.value || undefined;
  }

  getResult(struct = false) {
    const extra: { [key: string]: Value; } = {};
    for (const id in this.formGroup.controls) {
      if (id.startsWith('extra__')) {
        const control = this.formGroup.controls[id];
        if (control.value !== null) {
          const optionId = id.replace('extra__', '');

          if (struct) {
            extra[optionId] = this.toStructValue(optionId, control.value);
          } else {
            extra[optionId] = this.toYamcsValue(optionId, control.value);
          }
        }
      }
    }
    return extra;
  }

  private toStructValue(optionId: string, controlValue: any): any {
    let option: CommandOption;
    for (const candidate of this.commandOptions) {
      if (candidate.id === optionId) {
        option = candidate;
      }
    }
    switch (option!.type) {
      case 'BOOLEAN':
        return controlValue === 'true';
      case 'NUMBER':
        return Number(controlValue);
      default:
        return String(controlValue);
    }
  }

  private toYamcsValue(optionId: string, controlValue: any): Value {
    let option: CommandOption;
    for (const candidate of this.commandOptions) {
      if (candidate.id === optionId) {
        option = candidate;
      }
    }
    switch (option!.type) {
      case 'BOOLEAN':
        if (controlValue === 'true') {
          return { type: 'BOOLEAN', booleanValue: true };
        }
        return { type: 'BOOLEAN', booleanValue: false };
      case 'NUMBER':
        return { type: 'SINT32', sint32Value: Number(controlValue) };
      default:
        return { type: 'STRING', stringValue: String(controlValue) };
    }
  }
}
