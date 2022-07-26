import { StackEntry } from './StackEntry';

export class StackFormatter {

  private entries: StackEntry[];

  constructor(entries: StackEntry[] = []) {
    this.entries = entries;
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
          const value = entry.extra[id];
          switch (value.type) {
            case 'BOOLEAN':
              extraOptionEl.setAttribute('value', '' + value.booleanValue);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            case 'FLOAT':
              extraOptionEl.setAttribute('value', '' + value.floatValue);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            case 'DOUBLE':
              extraOptionEl.setAttribute('value', '' + value.doubleValue);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            case 'UINT32':
              extraOptionEl.setAttribute('value', '' + value.uint32Value);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            case 'SINT32':
              extraOptionEl.setAttribute('value', '' + value.sint32Value);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            case 'ENUMERATED':
            case 'STRING':
              extraOptionEl.setAttribute('value', '' + value.stringValue);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            case 'UINT64':
              extraOptionEl.setAttribute('value', '' + value.uint64Value);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            case 'SINT64':
              extraOptionEl.setAttribute('value', '' + value.sint64Value);
              extraOptionsEl.appendChild(extraOptionEl);
              break;
            default: // Ignore
          }
        }
        entryEl.appendChild(extraOptionsEl);
      }
      for (const argName in entry.args) {
        const argumentEl = doc.createElement('commandArgument');
        argumentEl.setAttribute('argumentName', argName);
        argumentEl.setAttribute('argumentValue', entry.args[argName]);
        entryEl.appendChild(argumentEl);
      }
      rootEl.appendChild(entryEl);
    }

    let xmlString = new XMLSerializer().serializeToString(rootEl);
    return this.formatXml(xmlString);
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
}
