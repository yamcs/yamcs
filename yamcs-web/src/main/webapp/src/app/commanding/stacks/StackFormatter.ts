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
      for (const argument of entry.arguments) {
        const argumentEl = doc.createElement('commandArgument');
        argumentEl.setAttribute('argumentName', argument.name);
        argumentEl.setAttribute('argumentValue', argument.value);
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
