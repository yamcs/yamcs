import { Value } from '../../../client';
import { Bounds } from './Bounds';
import { Color } from './Color';
import { Font } from './Font';

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
export function parseColorChild(parentNode: Element, defaultValue?: Color) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i] as Element;
    if (child.nodeName === 'color') {
      return parseColorNode(child);
    }
  }

  if (defaultValue !== undefined) {
    return defaultValue;
  } else {
    throw new Error(`No child node named 'color' could be found`);
  }
}

export function parseColorNode(node: Element) {
  const r = parseIntAttribute(node, 'red');
  const g = parseIntAttribute(node, 'green');
  const b = parseIntAttribute(node, 'blue');
  return new Color(r, g, b);
}

export function parseFontNode(node: Element) {
  if (hasChild(node, 'opifont.name')) {
    const fontNode = findChild(node, 'opifont.name');
    const name = parseStringAttribute(fontNode, 'fontName');
    const height = parseIntAttribute(fontNode, 'height');
    const style = parseIntAttribute(fontNode, 'style');
    let pixels = false;
    if (fontNode.hasAttribute('pixels')) {
      pixels = parseBooleanAttribute(fontNode, 'pixels');
    }
    return new Font(name, height, style, pixels);
  } else {
    const fontNode = findChild(node, 'fontdata');
    const name = parseStringAttribute(fontNode, 'fontName');
    const height = parseIntAttribute(fontNode, 'height');
    const style = parseIntAttribute(fontNode, 'style');
    return new Font(name, height, style, false);
  }
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

export function unwrapParameterValue(value: Value): any {
  switch (value.type) {
    case 'FLOAT':
      return value.floatValue;
    case 'DOUBLE':
      return value.doubleValue;
    case 'UINT32':
      return value.uint32Value;
    case 'SINT32':
      return value.sint32Value;
    case 'UINT64':
      return value.uint64Value;
    case 'SINT64':
      return value.sint64Value;
    case 'BOOLEAN':
      return value.booleanValue;
    case 'TIMESTAMP':
      return value.timestampValue;
    case 'BINARY':
      return window.atob(value.binaryValue!);
    case 'STRING':
      return value.stringValue;
  }
}

export function outline(x: number, y: number, width: number, height: number, strokeWidth: number): Bounds {
  const inset = Math.max(1, strokeWidth) / 2.0;
  const inset1 = Math.floor(inset);
  const inset2 = Math.ceil(inset);
  return {
    x: x + inset1,
    y: y + inset1,
    width: width - inset1 - inset2,
    height: height - inset1 - inset2,
  };
}

export function normalizePath(base: string, relPath: string) {
  base = '/' + base;
  let nUpLn;
  let sDir = '';
  const sPath = base.replace(/[^\/]*$/, relPath.replace(/(\/|^)(?:\.?\/+)+/g, '$1'));
  let nStart = 0;
  for (let nEnd; nEnd = sPath.indexOf('/../', nStart), nEnd > -1; nStart = nEnd + nUpLn) {
    nUpLn = /^\/(?:\.\.\/)*/.exec(sPath.slice(nEnd))![0].length;
    sDir = (sDir + sPath.substring(nStart, nEnd)).replace(
      new RegExp('(?:\\\/+[^\\\/]*){0,' + ((nUpLn - 1) / 3) + '}$'), '/');
  }
  return (sDir + sPath.substr(nStart)).substr(1);
}
