import { Value } from '../client';
import { AdvancementParams } from './AdvancementParams';
import { Step } from './Step';

export class StackFormatter {
  private steps: Step[];
  private stackOptions: { advancement?: AdvancementParams; };

  constructor(steps: Step[] = [], stackOptions: { advancement?: AdvancementParams; }) {
    this.steps = steps;
    this.stackOptions = stackOptions;
  }

  addStep(step: Step) {
    this.steps.push(step);
  }

  toXML() {
    const doc = document.implementation.createDocument(null, null, null);
    const rootEl = doc.createElement('commandStack');
    doc.appendChild(rootEl);

    for (const step of this.steps) {
      if (step.type !== 'command') {
        continue;
      }

      const stepEl = doc.createElement('command');
      stepEl.setAttribute('qualifiedName', step.name);
      if (step.comment) {
        stepEl.setAttribute('comment', step.comment);
      }
      if (step.extra) {
        const extraOptionsEl = doc.createElement('extraOptions');
        for (const id in step.extra) {
          const extraOptionEl = doc.createElement('extraOption');
          extraOptionEl.setAttribute('id', id);
          const value = this.getValue(step.extra[id]);
          if (value != null) {
            extraOptionEl.setAttribute('value', '' + value);
            extraOptionsEl.appendChild(extraOptionEl);
          }
        }
        stepEl.appendChild(extraOptionsEl);
      }
      for (const argName in step.args) {
        const argumentEl = doc.createElement('commandArgument');
        argumentEl.setAttribute('argumentName', argName);
        const argValue = this.formatValue(step.args[argName]);
        argumentEl.setAttribute('argumentValue', argValue);
        stepEl.appendChild(argumentEl);
      }
      rootEl.appendChild(stepEl);
    }

    let xmlString = new XMLSerializer().serializeToString(rootEl);
    return this.formatXml(xmlString);
  }

  private formatValue(value: any) {
    if (Array.isArray(value)) {
      return JSON.stringify(value);
    } else if (typeof value === 'object') {
      return JSON.stringify(value);
    } else {
      return String(value);
    }
  }

  private formatXml(xml: string) {
    let formatted = '';
    let indent = '';
    const spaces = '  ';
    xml.split(/>\s*</).forEach(function (node) {
      if (node.match(/^\/\w/)) indent = indent.substring(spaces.length);
      formatted += indent + '<' + node + '>\r\n';
      if (node.match(/^<?\w[^>]*[^\/]$/)) indent += spaces;
    });
    return formatted.substring(1, formatted.length - 3);
  }

  toJSON() {
    const root: { [key: string]: any; } = {
      '$schema': 'https://yamcs.org/schema/stack.schema.json',
      'steps': [],
    };
    for (const step of this.steps) {
      if (step.type === 'command') {
        root['steps'].push({
          type: step.type,
          name: step.name,
          ...(step.namespace && { namespace: step.namespace }),
          ...(step.comment && { comment: step.comment }),
          ...(step.extra && { extraOptions: this.getExtraOptionsJSON(step.extra) }),
          ...(step.args && { arguments: this.getCommandArgumentsJSON(step.args) }),
          ...(step.advancement && { advancement: step.advancement })
        });
      } else if (step.type === 'check') {
        root['steps'].push({
          type: step.type,
          ...(step.comment && { comment: step.comment }),
          parameters: [...step.parameters],
        });
      }
    }

    if (this.stackOptions.advancement) {
      root['advancement'] = this.stackOptions.advancement;
    }

    return JSON.stringify(root, null, 2);
  }

  private getExtraOptionsJSON(extra: { [key: string]: Value; }): any {
    let extraOptions = [];
    for (const id in extra) {
      const value = this.getValue(extra[id]);
      extraOptions.push({
        id: id,
        ...(value != null && { value: value })
      });
    }
    return extraOptions;
  }

  private getCommandArgumentsJSON(args: { [key: string]: any; }) {
    let commandArguments = [];
    for (const argName in args) {
      commandArguments.push({
        name: argName,
        value: args[argName]
      });
    }
    return commandArguments;
  }

  private getValue(value: Value) {
    switch (value.type) {
      case 'BOOLEAN':
        return value.booleanValue;
      case 'FLOAT':
        return value.floatValue;
      case 'DOUBLE':
        return value.doubleValue;
      case 'UINT32':
        return value.uint32Value;
      case 'SINT32':
        return value.sint32Value;
      case 'ENUMERATED':
      case 'STRING':
        return value.stringValue;
      case 'UINT64':
        return value.uint64Value;
      case 'SINT64':
        return value.sint64Value;
      default: // Ignore
    }
  }
}
