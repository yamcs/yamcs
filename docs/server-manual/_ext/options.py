import os
import re

import yaml
from docutils import nodes
from docutils.parsers import rst
from docutils.statemachine import ViewList
from sphinx import addnodes
from sphinx.directives.code import CodeBlock
from sphinx.util.docutils import SphinxDirective
from sphinx.util.nodes import nested_parse_with_titles


def produce_nodes(state, rst_text):
    # Deindent small indents to not trigger unwanted rst
    # blockquotes. This uses a simple algorithm that only
    # keeps indents in multiples of 4.
    deindented = []
    for line in rst_text.splitlines():
        indent_size = len(line) - len(line.lstrip())
        allowed_indent = int(indent_size / 4) * '    '
        deindented.append(allowed_indent + line.lstrip())

    unprocessed = ViewList()
    for line in deindented:
        unprocessed.append(line, 'fakefile.rst', 1)

    temp_node = nodes.section()
    temp_node.document = state.document
    nested_parse_with_titles(state, unprocessed, temp_node)
    return [node for node in temp_node.children]


class OptionsDirective(SphinxDirective):
    required_arguments = 1

    def run(self):
        result = []
        yaml_file = self.arguments[0]

        dl_items = []
        with open(yaml_file) as f:
            descriptor = yaml.load(f, Loader=yaml.FullLoader)
            for option_name, option in descriptor['options'].items():
                term_nodes = [
                    nodes.literal('', option_name),
                    nodes.Text(' ('),
                    nodes.Text(option['type'].lower()),
                    nodes.Text(')'),
                ]

                definition_nodes = []
                if 'description' in option:
                    for para in option['description']:
                        definition_nodes.append(nodes.paragraph(text=para))

                if 'default' in option:
                    default_value = option['default']
                    if option['type'] == 'BOOLEAN':  # True, False ==> true, false
                        default_value = str(default_value).lower()
                    default_nodes = [nodes.Text('Default: '), nodes.literal('', default_value)]
                    definition_nodes.append(nodes.paragraph('', '', *default_nodes))

                # TODO, shoud become rubrics
                if option['type'] == 'LIST':
                    continue

                dl_items.append(nodes.definition_list_item(
                    '',
                    nodes.term('', '', *term_nodes),
                    nodes.definition('', *definition_nodes)
                ))

        result += [nodes.definition_list('', *dl_items)]
        return result


def setup(app):
    app.add_directive('options', OptionsDirective)
