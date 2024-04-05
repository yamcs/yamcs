import { ChangeDetectionStrategy, Component, Input, OnInit } from '@angular/core';
import { ControlContainer, FormGroupName } from '@angular/forms';
import { ArgumentType, Value, WebappSdkModule, utils } from '@yamcs/webapp-sdk';
import { TemplateProvider } from '../../command-form/command-form.component';
import { AggregateArgumentComponent } from '../aggregate-argument/aggregate-argument.component';
import { ArrayArgumentComponent } from '../array-argument/array-argument.component';
import { BinaryArgumentComponent } from '../binary-argument/binary-argument.component';
import { BooleanArgumentComponent } from '../boolean-argument/boolean-argument.component';
import { EnumerationArgumentComponent } from '../enumeration-argument/enumeration-argument.component';
import { FloatArgumentComponent } from '../float-argument/float-argument.component';
import { IntegerArgumentComponent } from '../integer-argument/integer-argument.component';
import { StringArgumentComponent } from '../string-argument/string-argument.component';
import { TimeArgumentComponent } from '../time-argument/time-argument.component';

/**
 * Returns the stringified initial form value for a Value object.
 */
export function renderValue(value: Value): any {
  switch (value.type) {
    case 'BOOLEAN':
      return '' + value.booleanValue;
    case 'FLOAT':
      return '' + value.floatValue;
    case 'DOUBLE':
      return '' + value.doubleValue;
    case 'UINT32':
      return '' + value.uint32Value;
    case 'SINT32':
      return '' + value.sint32Value;
    case 'BINARY':
      return utils.convertBase64ToHex(value.binaryValue!);
    case 'ENUMERATED':
    case 'STRING':
    case 'TIMESTAMP':
      return value.stringValue!;
    case 'UINT64':
      return '' + value.uint64Value;
    case 'SINT64':
      return '' + value.sint64Value;
    case 'AGGREGATE':
      const { name: names, value: values } = value.aggregateValue!;
      const result: { [key: string]: any; } = {};
      for (let i = 0; i < names.length; i++) {
        result[names[i]] = renderValue(values[i]);
      }
      return result;
    case 'ARRAY':
      return value.arrayValue!.map(v => renderValue(v));
  }
}

/**
 * Returns the stringified initial form value for a JSON object.
 */
function renderJsonElement(jsonElement: any): any {
  if (Array.isArray(jsonElement)) {
    return jsonElement.map(el => renderJsonElement(el));
  } else if (typeof jsonElement === 'object') {
    const result: { [key: string]: any; } = {};
    for (const key in jsonElement) {
      result[key] = renderJsonElement(jsonElement[key]);
    }
    return result;
  } else {
    return '' + jsonElement;
  }
}

@Component({
  standalone: true,
  selector: 'app-argument',
  templateUrl: './argument.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  viewProviders: [{
    provide: ControlContainer,
    useExisting: FormGroupName,
  }],
  imports: [
    AggregateArgumentComponent,
    ArrayArgumentComponent,
    BinaryArgumentComponent,
    BooleanArgumentComponent,
    EnumerationArgumentComponent,
    FloatArgumentComponent,
    IntegerArgumentComponent,
    WebappSdkModule,
    StringArgumentComponent,
    TimeArgumentComponent,
  ],
})
export class ArgumentComponent implements OnInit {

  @Input()
  name: string;

  @Input()
  description?: string;

  @Input()
  type: ArgumentType;

  @Input()
  initialValue?: string;

  @Input()
  templateProvider: TemplateProvider;

  parsedInitialValue?: any;

  ngOnInit() {
    if (this.initialValue) {
      if (this.type.engType === 'AGGREGATE' || this.type.engType === 'ARRAY') {
        this.parsedInitialValue = renderJsonElement(JSON.parse(this.initialValue));
      } else if (this.type.engType === 'BOOLEAN') {
        this.parsedInitialValue = '' + (this.initialValue === this.type.oneStringValue);
      } else {
        this.parsedInitialValue = this.initialValue;
      }
    }

    if (this.templateProvider) {
      const previousValue = this.templateProvider.getAssignment(this.name);
      if (previousValue?.type === 'AGGREGATE') {
        this.parsedInitialValue = {
          ...this.parsedInitialValue || {},
          ...renderValue(previousValue),
        };
      } else if (previousValue?.type === 'ARRAY') {
        this.parsedInitialValue = renderValue(previousValue);
      }
    }
  }
}
