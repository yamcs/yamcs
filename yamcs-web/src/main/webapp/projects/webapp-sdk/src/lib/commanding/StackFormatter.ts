import { Value } from '../client';
import { AdvancementParams } from './AdvancementParams';
import { StackEntry } from './StackEntry';

export class StackFormatter {
  private entries: StackEntry[];
  private stackOptions: { advancement?: AdvancementParams; };

  constructor(entries: StackEntry[] = [], stackOptions: { advancement?: AdvancementParams; }) {
    this.entries = entries;
    this.stackOptions = stackOptions;
  }

  addEntry(entry: StackEntry) {
    this.entries.push(entry);
  }

  toXML() {
    const doc = document.implementation.createDocument(null, null, null);
    const rootEl = doc.createElement('commandStack');
    doc.appendChild(rootEl);

    for (const entry of this.entries) {
      const entryEl = doc.createElement('command');
      entryEl.setAttribute('qualifiedName', entry.name);
      if (entry.comment) {
        entryEl.setAttribute('comment', entry.comment);
      }
      if (entry.extra) {
        const extraOptionsEl = doc.createElement('extraOptions');
        for (const id in entry.extra) {
          const extraOptionEl = doc.createElement('extraOption');
          extraOptionEl.setAttribute('id', id);
          const value = this.getValue(entry.extra[id]);
          if (value != null) {
            extraOptionEl.setAttribute('value', '' + value);
            extraOptionsEl.appendChild(extraOptionEl);
          }
        }
        entryEl.appendChild(extraOptionsEl);
      }
      for (const argName in entry.args) {
        const argumentEl = doc.createElement('commandArgument');
        argumentEl.setAttribute('argumentName', argName);
        const argValue = this.formatValue(entry.args[argName]);
        argumentEl.setAttribute('argumentValue', argValue);
        entryEl.appendChild(argumentEl);
      }
      rootEl.appendChild(entryEl);
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
    return JSON.stringify({
      "$schema": "https://yamcs.org/schema/command-stack.schema.json",
      commands: this.entries.map(entry => {
        return {
          name: entry.name,
          ...(entry.namespace && { namespace: entry.namespace }),
          ...(entry.comment && { comment: entry.comment }),
          ...(entry.extra && { extraOptions: this.getExtraOptionsJSON(entry.extra) }),
          ...(entry.args && { arguments: this.getCommandArgumentsJSON(entry.args) }),
          ...(entry.advancement && { advancement: entry.advancement })
        };
      }),
      ...(this.stackOptions.advancement && { advancement: this.stackOptions.advancement }),
    }, null, 2);
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
