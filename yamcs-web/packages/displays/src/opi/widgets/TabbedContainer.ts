import { G, LinearGradient, Rect, Stop, Text } from '../../tags';
import { Color } from '../Color';
import { Font } from '../Font';
import { OpiDisplay } from '../OpiDisplay';
import * as utils from '../utils';
import { AbstractWidget } from './AbstractWidget';

interface Tab {
  title: string;
  backgroundColor: Color;
  foregroundColor: Color;
  enabled: boolean;
  font: Font;
  // iconPath: string;
  container?: G;
  containerEl?: Element;
}

const MARGIN = 10;
const GAP = 2;

export class TabbedContainer extends AbstractWidget {

  private minimumTabHeight: number;
  private activeTab: number;
  private horizontalTabs: boolean;
  private tabs: Tab[] = [];

  constructor(node: Element, display: OpiDisplay) {
    super(node, display);
    this.minimumTabHeight = utils.parseIntChild(node, 'minimum_tab_height');
    this.activeTab = utils.parseIntChild(node, 'active_tab');
    this.horizontalTabs = utils.parseBooleanChild(node, 'horizontal_tabs');
    const tabCount = utils.parseIntChild(node, 'tab_count');
    for (let i = 0; i < tabCount; i++) {
      const bgColorNode = utils.findChild(node, `tab_${i}_background_color`);
      const fgColorNode = utils.findChild(node, `tab_${i}_foreground_color`);
      const fontNode = utils.findChild(this.node, `tab_${i}_font`);
      this.tabs.push({
        title: utils.parseStringChild(node, `tab_${i}_title`),
        backgroundColor: utils.parseColorChild(bgColorNode),
        foregroundColor: utils.parseColorChild(fgColorNode),
        enabled: utils.parseBooleanChild(node, `tab_${i}_enabled`),
        font: utils.parseFontNode(fontNode),
      });
    }
  }

  draw(g: G) {
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-gradv`,
      x1: '0%',
      y1: '0%',
      x2: '0%',
      y2: '100%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '1' }),
      new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': '0' }),
    ));
    this.display.defs.addChild(new LinearGradient({
      id: `${this.id}-gradh`,
      x1: '0%',
      y1: '0%',
      x2: '100%',
      y2: '0%',
    }).addChild(
      new Stop({ offset: '0%', 'stop-color': Color.WHITE, 'stop-opacity': '1' }),
      new Stop({ offset: '100%', 'stop-color': Color.WHITE, 'stop-opacity': '0' }),
    ));
    if (this.horizontalTabs) {
      this.drawHorizontalTabs(g);
    } else {
      this.drawVerticalTabs(g);
    }
  }

  drawHorizontalTabs(g: G) {
    const tabAreaG = new G({
      transform: `translate(${this.x},${this.y + this.minimumTabHeight - 1})`
    });
    let x = this.x;
    for (let i = 0; i < this.tabs.length; i++) {
      const tab = this.tabs[i];
      const fm = this.getFontMetrics(tab.title, tab.font);
      let rectX;
      let rectY;
      let rectWidth;
      let rectHeight;
      let rectFill;
      if (this.activeTab === i) {
        rectX = x;
        rectY = this.y;
        rectWidth = fm.width + MARGIN + GAP;
        rectHeight = this.minimumTabHeight;
        rectFill = tab.backgroundColor;
      } else {
        rectX = x + GAP;
        rectY = this.y + 2;
        rectWidth = fm.width + MARGIN - GAP;
        rectHeight = this.minimumTabHeight - 2;
        rectFill = this.darken(tab.backgroundColor);
      }
      g.addChild(new Rect({
        id: `${this.id}-tabheader-${i}`,
        x: rectX,
        y: rectY,
        width: rectWidth,
        height: rectHeight,
        fill: rectFill,
      }));
      g.addChild(new Rect({
        x: rectX,
        y: rectY,
        width: rectWidth,
        height: rectHeight,
        fill: `url(#${this.id}-gradv)`,
        'pointer-events': 'none',
      }));
      g.addChild(new Text({
        x: rectX + (rectWidth / 2),
        y: rectY + (rectHeight / 2),
        'dominant-baseline': 'middle',
        'text-anchor': 'middle',
        'pointer-events': 'none',
        ...tab.font.getStyle(),
        fill: tab.foregroundColor,
      }, tab.title));
      x += fm.width + MARGIN - 1;

      tab.container = new G({
        id: `${this.id}-tabcontent-${i}`,
        style: `visibility: ${i === this.activeTab ? 'visible' : 'hidden'}`,
      });
      const tabContent = new Rect({
        x: 0,
        y: 0,
        width: this.width - 1,
        height: this.height - this.minimumTabHeight,
        fill: tab.backgroundColor,
      });
      tab.container.addChild(tabContent);
      tabAreaG.addChild(tab.container);

      for (const widgetNode of utils.findChildren(this.node, 'widget')) {
        if (utils.parseStringChild(widgetNode, 'name') === tab.title) {
          const widget = this.display.createWidget(widgetNode);
          if (widget) {
            widget.tag = widget.drawWidget();
            this.display.addWidget(widget, tab.container);
          }
        }
      }
    }
    g.addChild(tabAreaG);
  }

  drawVerticalTabs(g: G) {
    let y = this.y;
    let tabWidth = 0;
    for (const tab of this.tabs) {
      const fm = this.getFontMetrics(tab.title, tab.font);
      if (fm.width > tabWidth) {
        tabWidth = fm.width;
      }
    }

    const tabAreaG = new G({
      transform: `translate(${this.x + tabWidth - 1},${this.y})`,
    });

    for (let i = 0; i < this.tabs.length; i++) {
      const tab = this.tabs[i];
      let rectX;
      let rectY;
      let rectWidth;
      let rectHeight;
      let rectFill;
      if (this.activeTab === i) {
        rectX = this.x;
        rectY = y;
        rectWidth = tabWidth;
        rectHeight = this.minimumTabHeight + MARGIN + GAP;
        rectFill = tab.backgroundColor;
      } else {
        rectX = this.x + 2;
        rectY = y + GAP;
        rectWidth = tabWidth - 2;
        rectHeight = this.minimumTabHeight + MARGIN - GAP;
        rectFill = this.darken(tab.backgroundColor);
      }
      g.addChild(new Rect({
        id: `${this.id}-tabheader-${i}`,
        x: rectX,
        y: rectY,
        width: rectWidth,
        height: rectHeight,
        fill: rectFill,
      }));
      g.addChild(new Rect({
        x: rectX,
        y: rectY,
        width: rectWidth,
        height: rectHeight,
        fill: `url(#${this.id}-gradh)`,
        'pointer-events': 'none',
      }));
      g.addChild(new Text({
        x: rectX + (rectWidth / 2),
        y: rectY + (rectHeight / 2),
        'dominant-baseline': 'middle',
        'text-anchor': 'middle',
        'pointer-events': 'none',
        ...tab.font.getStyle(),
        fill: tab.foregroundColor,
      }, tab.title));
      y += this.minimumTabHeight + MARGIN - 1;

      tab.container = new G({
        id: `${this.id}-tabcontent-${i}`,
        style: `opacity: ${i === this.activeTab ? 1 : 0}`,
      });
      const tabContent = new Rect({
        x: 0,
        y: 0,
        width: this.width - tabWidth,
        height: this.height - 1,
        fill: tab.backgroundColor,
      });
      tab.container.addChild(tabContent);
      tabAreaG.addChild(tab.container);

      for (const widgetNode of utils.findChildren(this.node, 'widget')) {
        if (utils.parseStringChild(widgetNode, 'name') === tab.title) {
          const widget = this.display.createWidget(widgetNode);
          if (widget) {
            widget.tag = widget.drawWidget();
            this.display.addWidget(widget, tab.container);
          }
        }
      }
    }
    g.addChild(tabAreaG);
  }

  afterDomAttachment() {
    for (let i = 0; i < this.tabs.length; i++) {
      const headerEl = this.svg.getElementById(`${this.id}-tabheader-${i}`);
      headerEl.addEventListener('click', () => {
        for (let j = 0; j < this.tabs.length; j++) {
          const el = this.svg.getElementById(`${this.id}-tabcontent-${j}`) as SVGGElement;
          el.style.visibility = (this.tabs[i] === this.tabs[j]) ? 'visible' : 'hidden';
        }
      });
    }
  }

  private darken(color: Color) {
    const r = Math.max(0, color.red - 30);
    const g = Math.max(0, color.green - 30);
    const b = Math.max(0, color.blue - 30);
    return new Color(r, g, b);
  }
}
