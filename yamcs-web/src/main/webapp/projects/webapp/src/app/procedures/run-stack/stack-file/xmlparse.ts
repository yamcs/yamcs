import { CommandOption, CommandStep, Value } from '@yamcs/webapp-sdk';

export function parseXML(text: string, commandOptions: CommandOption[]): CommandStep[] {
  const xmlParser = new DOMParser();
  const doc = xmlParser.parseFromString(text, 'text/xml') as XMLDocument;
  return parseRoot(doc.documentElement, commandOptions);
}

function parseRoot(root: Node, commandOptions: CommandOption[]) {
  const entries: CommandStep[] = [];
  for (let i = 0; i < root.childNodes.length; i++) {
    const child = root.childNodes[i];
    if (child.nodeType !== 3) { // Ignore text or whitespace
      if (child.nodeName === 'command') {
        const entry = parseEntry(child as Element, commandOptions);
        entries.push(entry);
      }
    }
  }

  return entries;
}

function parseEntry(node: Element, commandOptions: CommandOption[]): CommandStep {
  const args: { [key: string]: any; } = {};
  const extra: { [key: string]: Value; } = {};
  for (let i = 0; i < node.childNodes.length; i++) {
    const child = node.childNodes[i] as Element;
    if (child.nodeName === 'commandArgument') {
      const argumentName = getStringAttribute(child, 'argumentName');
      args[argumentName] = getStringAttribute(child, 'argumentValue');
      if (args[argumentName] === 'true') {
        args[argumentName] = true;
      } else if (args[argumentName] === 'false') {
        args[argumentName] = false;
      }
    } else if (child.nodeName === 'extraOptions') {
      for (let j = 0; j < child.childNodes.length; j++) {
        const extraChild = child.childNodes[j] as Element;
        if (extraChild.nodeName === 'extraOption') {
          const id = getStringAttribute(extraChild, 'id');
          const stringValue = getStringAttribute(extraChild, 'value');
          const value = convertOptionStringToValue(id, stringValue, commandOptions);
          if (value) {
            extra[id] = value;
          }
        }
      }
    }
  }
  const entry: CommandStep = {
    type: 'command',
    name: getStringAttribute(node, 'qualifiedName'),
    args,
    extra,
  };

  if (node.hasAttribute('comment')) {
    entry.comment = getStringAttribute(node, 'comment');
  }

  return entry;
}

function getStringAttribute(node: Node, name: string) {
  const attr = (node as Element).attributes.getNamedItem(name);
  if (attr === null) {
    throw new Error(`No attribute named ${name}`);
  } else {
    return attr.textContent || '';
  }
}

function convertOptionStringToValue(id: string, value: string, commandOptions: CommandOption[]): Value | null {
  for (const option of commandOptions) {
    if (option.id === id) {
      switch (option.type) {
        case 'BOOLEAN':
          return { type: 'BOOLEAN', booleanValue: value === 'true' };
        case 'NUMBER':
          return { type: 'SINT32', sint32Value: Number(value) };
        default:
          return { type: 'STRING', stringValue: value };
      }
    }
  }
  return null;
}
