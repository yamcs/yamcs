import { FontResolver } from '@yamcs/opi';
import { Font } from '@yamcs/opi';

export class OpiDisplayFontResolver implements FontResolver {

  constructor(private prefix: string) {
  }

  resolve(font: Font): FontFace | undefined {
      let file;
      if (font.name === "Liberation Sans") {
        file = "LiberationSans-Regular.woff";
        if (font.bold && font.italic) {
          file = "LiberationSans-BoldItalic.woff";
        } else if (font.bold) {
          file = "LiberationSans-Bold.woff";
        } else if (font.italic) {
          file = "LiberationSans-Italic.woff";
        }
      } else if (font.name === "Liberation Mono") {
        file = "LiberationMono-Regular.woff";
        if (font.bold && font.italic) {
          file = "LiberationMono-BoldItalic.woff";
        } else if (font.bold) {
          file = "LiberationMono-Bold.woff";
        } else if (font.italic) {
          file = "LiberationMono-Italic.woff";
        }
      } else if (font.name === "Liberation Serif") {
        file = "LiberationMSerif-Regular.woff";
        if (font.bold && font.italic) {
          file = "LiberationSerif-BoldItalic.woff";
        } else if (font.bold) {
          file = "LiberationSerif-Bold.woff";
        } else if (font.italic) {
          file = "LiberationSerif-Italic.woff";
        }
      }

      if (file) {
        return new FontFace(font.name, `url(${this.prefix}${file})`, {
          weight: font.bold ? "bold" : "normal",
          style: font.italic ? "italic" : "normal",
        });
      }
  }
}
