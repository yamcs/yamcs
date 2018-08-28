import { Color } from './Color';

/**
 * Searches the given node for a direct child with the specified name.
 *
 * @throws when no such child was found
 */
export function findChild(parentNode: Node, childNodeName: string) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (child.nodeName === childNodeName) {
      return child as Element;
    }
  }

  throw new Error(`No child node named ${childNodeName} could be found`);
}

export function hasChild(parentNode: Element, childNodeName: string) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (child.nodeName === childNodeName) {
      return true;
    }
  }
  return false;
}

/**
 * Searches the given node for all direct children with the specified name.
 * If this name is undefined, all children will be included in the result
 * array.
 */
export function findChildren(parentNode: Element, childNodeName?: string) {
  const matchingChildren: Element[] = [];
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i] as Element;
    if (child.nodeType !== 3) { // Ignore text or whitespace
      if (!childNodeName || (child.nodeName === childNodeName)) {
        matchingChildren.push(child);
      }
    }
  }
  return matchingChildren;
}

/**
 * Parses the child node contents of the given parent node as a string.
 *
 * @throws when no such child was found and defaultValue was undefined
 */
export function parseStringChild(parentNode: Node, childNodeName: string, defaultValue?: string) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (child.nodeName === childNodeName) {
      if (child.textContent !== null) {
        return child.textContent;
      }
    }
  }

  if (defaultValue !== undefined) {
    return defaultValue;
  } else {
    throw new Error(`No child node named ${childNodeName} could be found`);
  }
}

/**
 * Parses the child node contents of the given parent node as an integer.
 *
 * @throws when no such child was found and defaultValue was undefined.
 */
export function parseIntChild(parentNode: Node, childNodeName: string, defaultValue?: number) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (child.nodeName === childNodeName) {
      if (child.textContent !== null) {
        return parseInt(child.textContent, 10);
      }
    }
  }

  if (defaultValue !== undefined) {
    return defaultValue;
  } else {
    throw new Error(`No child node named ${childNodeName} could be found`);
  }
}

/**
 * Parses the child node contents of the given parent node as a float.
 *
 * @throws when no such child was found and defaultValue was undefined.
 */
export function parseFloatChild(parentNode: Node, childNodeName: string, defaultValue?: number) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (child.nodeName === childNodeName) {
      if (child.textContent !== null) {
        return parseFloat(child.textContent);
      }
    }
  }

  if (defaultValue !== undefined) {
    return defaultValue;
  } else {
    throw new Error(`No child node named ${childNodeName} could be found`);
  }
}

/**
 * Parses the child node contents of the given parent node as a boolean.
 *
 * @throws when no such child was found and defaultValue was undefined.
 */
export function parseBooleanChild(parentNode: Node, childNodeName: string, defaultValue?: boolean) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (child.nodeName === childNodeName) {
      return child.textContent === 'true';
    }
  }

  if (defaultValue !== undefined) {
    return defaultValue;
  } else {
    throw new Error(`No child node named ${childNodeName} could be found`);
  }
}

/**
 * Parses the child node contents of the given parent node as a color.
 *
 * @throws when no such child was found and defaultValue was undefined.
 */
export function parseColorChild(parentNode: Element, childNodeName: string, defaultValue?: Color) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i] as Element;
    if (child.nodeName === childNodeName) {
      return parseColorNode(child);
    }
  }

  if (defaultValue !== undefined) {
    return defaultValue;
  } else {
    throw new Error(`No child node named ${childNodeName} could be found`);
  }
}

export function parseColorNode(node: Element) {
  const r = parseIntAttribute(node, 'red');
  const g = parseIntAttribute(node, 'green');
  const b = parseIntAttribute(node, 'blue');
  return new Color(r, g, b);
}

export function parseStringAttribute(node: Element, attributeName: string) {
  const attr = node.attributes.getNamedItem(attributeName);
  if (attr === null) {
    throw new Error(`No attribute named ${attributeName}`);
  } else {
    return attr.textContent || '';
  }
}

export function parseIntAttribute(node: Element, attributeName: string) {
  const attr = node.attributes.getNamedItem(attributeName);
  if (attr === null) {
    throw new Error(`No attribute named ${attributeName}`);
  } else {
    return parseInt(attr.textContent || '', 10);
  }
}

export function parseBooleanAttribute(node: Element, attributeName: string) {
  const attr = node.attributes.getNamedItem(attributeName);
  if (attr === null) {
    throw new Error(`No attribute named ${attributeName}`);
  } else {
    return attr.textContent === 'true';
  }
}
