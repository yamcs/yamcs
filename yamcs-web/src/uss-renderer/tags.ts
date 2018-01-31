export class Tag {
  name: string;
  attributes: { [key: string]: string };
  children: Tag[] = [];
  innerText: string | undefined;

  constructor(name: string, attributes?: {}, innerText?: string) {
    this.name = name;
    this.attributes = attributes || {};
    this.innerText = innerText;
  }

  /**
   * Adds one or more child tags to this parent tag
   */
  addChild(...child: Tag[]) {
    for (const c of child) {
      this.children.push(c);
    }
    return this;
  }

  setAttribute(name: string, value: string) {
    this.attributes[name] = value;
  }

  /**
   * Recursively transforms this tag and its children into DOM elements
   */
  toDomElement(): SVGElement {
    const newElement = document.createElementNS('http://www.w3.org/2000/svg', this.name);
    for (const att of Object.keys(this.attributes)) {
      if (att === 'xlink:href') {
        newElement.setAttributeNS('http://www.w3.org/1999/xlink', att, this.attributes[att]);
      } else {
        newElement.setAttribute(att, this.attributes[att]);
      }
    }

    for (const nodeChild of this.children) {
      newElement.appendChild(nodeChild.toDomElement());
    }

    if (this.innerText) {
      const textNode = document.createTextNode(this.innerText);
      newElement.appendChild(textNode);
    }

    return newElement;
  }

  toString() {
    return this.stringRepresentation(true);
  }

  /**
   * Builds a string representation of this Tag.
   *
   * @param addNamespace When true the xmlns attribute will get added to the root element.
   * @param singleQuotes When true all attributes will get surrounded by single quotes
   * instead of double quotes. This can be useful to avoid URI-encoding bloat.
   */
  protected stringRepresentation(addNamespace = false, singleQuotes = false) {
    let result = '<' + this.name;
    if (addNamespace) {
      if (singleQuotes) {
        result += ' xmlns=\'http://www.w3.org/2000/svg\'';
      } else {
        result += ' xmlns="http://www.w3.org/2000/svg"';
      }
    }
    for (const att of Object.keys(this.attributes)) {
      if (singleQuotes) {
        result += ` ${att}='${this.attributes[att]}'`;
      } else {
        result += ` ${att}="${this.attributes[att]}"`;
      }
    }
    result += '>';

    for (const nodeChild of this.children) {
      result += nodeChild.stringRepresentation(false, singleQuotes);
    }

    if (this.innerText) {
      result += this.innerText;
    }
    result += '</' + this.name + '>';
    return result;
  }
}

export class Circle extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('circle', attributes, innerText);
  }
}

export class ClipPath extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('clipPath', attributes, innerText);
  }
}

export class Defs extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('defs', attributes, innerText);
  }
}

export class Desc extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('desc', attributes, innerText);
  }
}

export class Ellipse extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('ellipse', attributes, innerText);
  }
}

export class G extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('g', attributes, innerText);
  }
}

export class Image extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('image', attributes, innerText);
  }
}

export class Line extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('line', attributes, innerText);
  }
}

export class Marker extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('marker', attributes, innerText);
  }
}

export class Path extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('path', attributes, innerText);
  }
}

export class Pattern extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('pattern', attributes, innerText);
  }
}

export class Polygon extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('polygon', attributes, innerText);
  }
}

export class Polyline extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('polyline', attributes, innerText);
  }
}

export interface RectBorderBox {
  x: number;
  y: number;
  width: number;
  height: number;
  'stroke-width': number;
}

/**
 * A standard SVG Rect where strokes are rendered
 * on the center of the boundary.
 */
export class Rect extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('rect', attributes, innerText);
  }

  /**
   * Sets the specified attributes assuming they use border-box
   * coordinates. They will automatically be converted to content-box
   * based on the border thickness.
   *
   * Attention: stroke-width must already be set, or this method will
   * have no effect.
   */
  withBorderBox(x: number, y: number, width: number, height: number) {
    if ('stroke-width' in this.attributes) {
      const strokeWidth = Number(this.attributes['stroke-width']);
      if (strokeWidth) {
        x = x + (strokeWidth / 2.0);
        y = y + (strokeWidth / 2.0);
        width = width - strokeWidth;
        height = height - strokeWidth;
      }
    }

    this.attributes['x'] = String(x);
    this.attributes['y'] = String(y);
    this.attributes['width'] = String(width);
    this.attributes['height'] = String(height);
    return this;
  }
}

export class Set extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('set', attributes, innerText);
  }
}

export class Stop extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('stop', attributes, innerText);
  }
}

export class Svg extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('svg', attributes, innerText);
  }

  /**
   * Returns an Image Tag wrapping this tag and all of its children via a data uri.
   * This can be used to reduce DOM size at the price of removing interactivity.
   *
   * @param attributes attributes for the returned Image Tag
   */
  asImage(attributes = {}): Image {
    const tag = new Image(attributes);
    const encoded = encodeURIComponent(this.stringRepresentation(true, true));
    tag.setAttribute('xlink:href', 'data:image/svg+xml,' + encoded);
    return tag;
  }
}

export class Text extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('text', attributes, innerText);
  }
}

export class Title extends Tag {
  constructor(attributes?: {}, innerText?: string) {
    super('title', attributes, innerText);
  }
}
