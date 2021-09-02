import re

from pygments.lexer import RegexLexer
from pygments.token import *


class URITemplateLexer(RegexLexer):
    name = "URI Template"
    aliases = ["uritemplate"]

    flags = re.DOTALL

    tokens = {
        "root": [
            (r"(DELETE|GET|HEAD|OPTIONS|PATCH|POST|PUT) ", Name.Function),
            (r"[\/\:][^\{]*", Text),
            (r"\{", Punctuation, "variable"),
        ],
        "variable": [
            (r"\{", Punctuation, "#push"),
            (r"\}", Punctuation, "#pop"),
            (r"[\*\?]+", Punctuation),
            (r"[^\}\*\?]+", Name.Variable),
        ],
    }


class URIVariableLexer(RegexLexer):
    name = "URI Variable"
    aliases = ["urivariable"]

    tokens = {
        "root": [
            (r"[^\{\}]*", Name.Variable),
            (r"\{", Punctuation),
            (r"\}", Punctuation),
        ]
    }
