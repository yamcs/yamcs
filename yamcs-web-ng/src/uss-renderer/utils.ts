import { DataBinding } from './DataBinding';


let computationCount = 0;

const opsNamespace = 'MDB:OPS Name';

/**
 * Searches the given node for a direct child with the specified name.
 *
 * @throws when no such child was found
 */
export function findChild(parentNode: Node, childNodeName: string) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (child.nodeName === childNodeName) {
      return child;
    }
  }

  throw new Error(`No child node named ${childNodeName} could be found`);
}

/**
 * Searches the given node for all direct children with the specified name.
 * If this name is undefined, all children will be included in the result
 * array.
 */
export function findChildren(parentNode: Node, childNodeName?: string) {
  const matchingChildren = [];
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
    if (!childNodeName || (child.nodeName === childNodeName)) {
      matchingChildren.push(child);
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
      if (child.nodeValue !== null) {
        return child.nodeValue;
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
      if (child.nodeValue !== null) {
        return parseInt(child.nodeValue, 10);
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
      if (child.nodeValue !== null) {
        return parseFloat(child.nodeValue);
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
      return child.nodeValue === 'true';
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
export function parseColorChild(parentNode: Node, childNodeName: string, defaultValue?: string) {
  for (let i = 0; i < parentNode.childNodes.length; i++) {
    const child = parentNode.childNodes[i];
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

export function parseColorNode(node: Node) {
  const r = parseStringChild(node, 'red');
  const g = parseStringChild(node, 'green');
  const b = parseStringChild(node, 'blue');
  const a = parseStringChild(node, 'alpha');
  return `rgba(${r},${g},${b},${a})`;
}

export function parseDataBinding(e: Node) {
  const db = new DataBinding();
  db.dynamicProperty = parseStringChild(e, 'DynamicProperty');
  let ds = findChild(e, 'DataSource');
  if (ds.hasAttribute('reference')) {
    ds = getReferencedElement(ds);
  }
  db.type = ds.getAttribute('class');
  if (db.type === 'ExternalDataSource') {
    $('Names entry', ds).each((idx: any, val: any) => {
      const n = $('string:nth-child(1)', val).text(); // one of 'Opsname', 'Pathname' or 'SID'
      db[n] = $('string:nth-child(2)', val).text();
    });
    if (db.Opsname !== undefined) {
      db.parameterName = db.Opsname.trim();
      db.parameterNamespace = opsNamespace.trim();
    } else {
      console.log('External Data source without Opsname', ds);
      return;
    }
    db.usingRaw = parseBooleanChild(ds, 'UsingRaw');
  } else if (db.type === 'Computation') {
    const pname = '__uss_computation' + computationCount;
    computationCount++;
    const c = new Object();
    c.expression = parseStringChild(ds, 'Expression');
    c.args = [];
    c.parameterName = pname;

    $('Arguments ExternalDataSource', e).each((idx: any, val: any) => {
      const arg = new Object();
      $('Names entry', val).each((idx1: any, val1: any) => {
        const n = $('string:nth-child(1)', val1).text(); // one of 'Opsname', 'Pathname' or 'SID'
        arg[n] = $('string:nth-child(2)', val1).text();
      });
      c.args.push(arg);
    });
    const names = $(ds).children('Names');
    $('entry', names).each((idx: any, val: any) => {
      const n = $('string:nth-child(1)', val).text(); // DEFAULT
      db[n] = $('string:nth-child(2)', val).text();
    });
    db.expression = c.expression;
    db.args = c.args;
    db.parameterName = pname;
  }
  return db;
}

/*
 * Writes text in the bounding box opts:x,y,width,height
 * TODO: only works for left to right horizontal text
 */
export function writeText(svg: any, parent: any, opts: any, textStyle: Node, text: any) {
  const settings: { [key: string]: string } = {
    id: opts.id,
    ...parseTextStyle(textStyle),
  };

  const horizAlignment = parseStringChild(textStyle, 'HorizontalAlignment').toLowerCase();
  const vertAlignment = parseStringChild(textStyle, 'VerticalAlignment').toLowerCase();
  let x;
  if (horizAlignment === 'center') {
    x = opts.x + opts.width / 2;
    settings.textAnchor = 'middle';
  } else if (horizAlignment === 'left') {
    x = opts.x;
    settings.textAnchor = 'start';
  } else if (horizAlignment === 'right') {
    x = opts.x + opts.width;
    settings.textAnchor = 'end';
  }

  text = text.split(' ').join('\u00a0'); // Preserve whitespace
  const t = svg.text(parent, x, opts.y, text, settings);
  const bbox = t.getBBox();
  // shift to have the bbox correspond to x,y,width,height
  if (vertAlignment === 'center') {
    t.setAttribute('dy', opts.y - bbox.y + (opts.height - bbox.height) / 2);
  } else if (vertAlignment === 'top') {
    t.setAttribute('dy', opts.y - bbox.y);
  } else if (vertAlignment === 'bottom') {
    t.setAttribute('dy', opts.y - bbox.y + opts.height - bbox.height);
  }
  return t;
}

export function parseFillStyle(node: Node) {
  const fillStyleNode = findChild(node, 'FillStyle');
  const pattern = parseStringChild(fillStyleNode, 'Pattern');
  return {
    fill: parseColorChild(fillStyleNode, 'Color'),
    fillOpacity: (pattern.toLowerCase() === 'solid') ? 1 : 0,
  };
}

export function parseDrawStyle(node: Node) {
  const drawStyleNode = findChild(node, 'DrawStyle');
  const pattern = parseStringChild(drawStyleNode, 'Pattern');
  return {
    stroke: parseColorChild(drawStyleNode, 'Color'),
    strokeOpacity: (pattern.toLowerCase() === 'solid') ? 1 : 0,
    strokeWidth: parseStringChild(drawStyleNode, 'Width'),
  };
}

export function parseTextStyle(e: Node) {
  const style: { [key: string]: string } = {};
  style.fill = parseColorChild(e, 'Color');
  style.fontSize = parseStringChild(e, 'Fontsize') + 'px';
  style.fontFamily = parseStringChild(e, 'Fontname');
  if (style.fontFamily === 'Lucida Sans Typewriter') {
      style.fontFamily = 'Lucida Sans Typewriter, monospace';
  }
  if (parseBooleanChild(e, 'IsBold', false)) {
    style.fontWeight = 'bold';
  }
  if (parseBooleanChild(e, 'IsItalic', false)) {
    style.fontStyle = 'italic';
  }
  if (parseBooleanChild(e, 'IsUnderlined', false)) {
    style.textDecoration = 'underline';
  }

  return style;
}

export function parseStringAttribute(node: Node, attributeName: string) {
  const value = node.attributes.getNamedItem(attributeName).nodeValue;
  if (value === null) {
    throw new Error(`No attribute named ${attributeName}`);
  } else {
    return value;
  }
}

export function getReferencedElement(node: Node) {
  let e = node;

  const reference = parseStringAttribute(e, 'reference');
  for (const token of reference.split('/')) {
    if (token === '..') {
      if (!e.parentNode) {
        throw new Error('No such parent');
      }
      e = e.parentNode;
    } else {
      let idx = 0;
      const k = token.indexOf('[');
      let nodeName = token;
      if (k !== -1) {
        const idxStr = token.substring(k + 1, token.indexOf(']', k));
        idx = parseInt(idxStr, 10) - 1;
        nodeName = token.substring(0, k);
      }
      e = findChildren(e, nodeName)[idx];
    }
  }
  return e;
}

// Creates a definition section in the SVG and adds the markers that will be used for polylines arrows
// TODO: It is broken currently because the markers will show all in black, instead of the color of the line
export function addArrowMarkers(svg: any) {
  const defs = svg.defs();

  const settings = {overflow: 'visible', fill: 'currentColor', stroke: 'none'};
  const arrowMarkerStart = svg.marker(defs, 'uss-arrowStart', 0, 0, 20, 20, 'auto', settings);

  let path = svg.createPath();
  svg.path(arrowMarkerStart, path.move(0, -15).line(-20, 0).line(0, 15),
      {fillRule: 'evenodd', fillOpacity: '1.0', transform: 'scale(0.2, 0.2) translate(20, 0)'});

  const arrowMarkerEnd = svg.marker(defs, 'uss-arrowEnd', 0, 0, 20, 20, 'auto', settings);

  path = path.reset();
  svg.path(arrowMarkerEnd, path.move(0, -15).line(-20, 0).line(0, 15),
      {fillRule: 'evenodd', fillOpacity: '1.0', transform: 'scale(0.2, 0.2) rotate(180) translate(20, 0)'});
}
