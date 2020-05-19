import os
import re

from google.protobuf import descriptor_pb2
from google.protobuf.descriptor import FileDescriptor, MakeDescriptor
from google.protobuf.descriptor_database import DescriptorDatabase

import lexers
from docutils import nodes
from docutils.parsers import rst
from docutils.statemachine import ViewList
from sphinx import addnodes
from sphinx.directives.code import CodeBlock
from sphinx.util.docutils import SphinxDirective
from sphinx.util.nodes import nested_parse_with_titles
from yamcs.api import annotations_pb2

descriptors_by_symbol = {}
comments_by_symbol = {}
package_by_symbol = {}

DEFAULT_EXCLUDES = [
    '.google.protobuf.Timestamp'
]

template_dir = os.path.join(os.path.abspath('_ext'), 'templates')

class ProtoDirective(CodeBlock):
    required_arguments = 1

    def __init__(self, *args, **kwargs):
        super(ProtoDirective, self).__init__(*args, **kwargs)
        symbol = self.arguments[0]
        self.arguments = ['typescript']
        self.content = [describe_message(symbol)]


def find_related_types(symbols, excluded_types):
    related_types = []
    excluded_types += symbols[:]
    for symbol in symbols:
        descriptor = descriptors_by_symbol[symbol]    
        for field in descriptor.field:
            if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE:
                nested_type = descriptors_by_symbol[field.type_name]
                if nested_type.options.map_entry:
                    continue

                if field.type_name not in excluded_types:
                    related_types += [field.type_name]
                    related_types += find_related_types([field.type_name], excluded_types)
    return related_types


def find_related_enums(symbols, excluded_types):
    related_enums = []
    excluded_types += symbols[:]
    for symbol in symbols:
        descriptor = descriptors_by_symbol[symbol]
        for field in descriptor.field:
            if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_ENUM:
                if field.type_name not in related_enums:
                    related_enums.append(field.type_name)
            elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE:
                if field.type_name not in excluded_types:
                    related_enums += find_related_enums([field.type_name], excluded_types)
    return related_enums


def get_route_for_method_descriptor(descriptor, addmethod=True):
    route_options = descriptor.options.Extensions[annotations_pb2.route]
    if route_options.HasField('post'):
        return 'POST ' + route_options.post if addmethod else route_options.post
    if route_options.HasField('get'):
        return 'GET ' + route_options.get if addmethod else route_options.get
    if route_options.HasField('delete'):
        return 'DELETE ' + route_options.delete if addmethod else route_options.delete
    if route_options.HasField('put'):
        return 'PUT ' + route_options.put if addmethod else route_options.put
    if route_options.HasField('patch'):
        return 'PATCH ' + route_options.patch if addmethod else route_options.patch
    return None


def get_route_param_template(uri_template, param):
    return re.search(r'(\{' + param + r'[\*\?]*\})', uri_template).group(1)


def get_route_params(uri_template):
    return [
        p for p in re.findall(r'\{([^\}\*\?]*)[\*\?]*\}', uri_template)
    ]


class RPCDirective(CodeBlock):
    required_arguments = 1
    own_option_spec = dict(input=bool,
                           output=bool,
                           related=bool)

    option_spec = CodeBlock.option_spec.copy()
    option_spec.update(own_option_spec)

    def __init__(self, *args, **kwargs):
        super(RPCDirective, self).__init__(*args, **kwargs)
        symbol = self.arguments[0]
        self.arguments = ['typescript']
        
        descriptor = descriptors_by_symbol[symbol]
        body_symbol = self.get_body_symbol(descriptor)

        self.content = []
        if 'input' in self.options:
            excluded_fields = []

            if descriptor.options.HasExtension(annotations_pb2.route):
                route = get_route_for_method_descriptor(descriptor)
                # Remove route params from the message. Transcoding
                # fetches them from the URL directly
                excluded_fields += get_route_params(route)

            self.content.append(describe_message(
                body_symbol,
                excluded_fields=excluded_fields,
            ))
        if 'output' in self.options:
            self.content.append(describe_message(descriptor.output_type))
        if 'related' in self.options:
            related_types = find_related_types([
                body_symbol or descriptor.input_type,
                descriptor.output_type,
            ], DEFAULT_EXCLUDES[:])
            for related_type in related_types:
                self.content.append(describe_message(related_type))

            related_enums = find_related_enums([
                body_symbol or descriptor.input_type,
                descriptor.output_type,
            ], DEFAULT_EXCLUDES[:])
            for related_enum in related_enums:
                self.content.append(describe_enum(related_enum))

    def get_body_symbol(self, method_descriptor):
        # Transcoding would promote the body field to the actual
        # expected request body. (The actual input_type is in this
        # case only used for query/route parameters).
        if method_descriptor.options.HasExtension(annotations_pb2.route):
            route_options = method_descriptor.options.Extensions[annotations_pb2.route]
            if route_options.HasField('body') and route_options.body != '*':
                input_descriptor = descriptors_by_symbol[method_descriptor.input_type]
                for field in input_descriptor.field:
                    if field.json_name == route_options.body:
                        return field.type_name

        return method_descriptor.input_type


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


class WebSocketDirective(SphinxDirective):
    required_arguments = 1

    def run(self):
        result = []
        symbol = self.arguments[0]
        descriptor = descriptors_by_symbol[symbol]

        comment = find_comment(symbol, prefix='')
        if comment:
            result += produce_nodes(self.state, comment)

        return result


class ServiceDirective(SphinxDirective):
    required_arguments = 1

    def run(self):
        result = []
        symbol = self.arguments[0]
        descriptor = descriptors_by_symbol[symbol]

        comment = find_comment(symbol, prefix='')
        if comment:
            result += produce_nodes(self.state, comment)

        return result


class RouteDirective(SphinxDirective):
    required_arguments = 1

    def run(self):
        result = []
        symbol = self.arguments[0]
        descriptor = descriptors_by_symbol[symbol]

        comment = find_comment(symbol, prefix='')
        if comment:
            result += produce_nodes(self.state, comment)
        
        if descriptor.client_streaming:
            text = (
                'This method uses client-streaming.'
            )
            result.append(nodes.warning(
                '',
                nodes.paragraph('', '', nodes.Text(text)),
            ))

        if descriptor.server_streaming:
            text = (
                'This method uses server-streaming. ' +
                'Yamcs sends an unspecified amount of data ' +
                'using chunked transfer encoding.'
            )
            result.append(nodes.warning(
                '',
                nodes.paragraph('', '', nodes.Text(text)),
            ))

        route_options = descriptor.options.Extensions[annotations_pb2.route]
        route_text = get_route_for_method_descriptor(descriptor)

        raw = '.. rubric:: URI Template\n'
        raw += '.. code-block:: uritemplate\n\n'
        raw += '    ' + route_text + '\n'

        result += produce_nodes(self.state, raw)

        input_descriptor = descriptors_by_symbol[descriptor.input_type]

        route_params = get_route_params(route_text)
        if route_params:
            dl_items = []
            for param in route_params:
                param_template = get_route_param_template(route_text, param)
                comment = find_comment(descriptor.input_type + '.' + param, prefix='') or ''

                # Highlight individual variable
                # FIXME doesn't seem to find lexer
                raw = '``' + param_template + '``'
                #raw = '.. role:: urivariable(code)\n'
                #raw += '    :language: urivariable\n\n'
                #raw += ':urivariable:`' + param_template  + '`'
                param_template_node = produce_nodes(self.state, raw)[0]

                dl_items.append(nodes.definition_list_item(
                    '',
                    nodes.term('', '', param_template_node),
                    nodes.definition('', nodes.paragraph(text=comment)),
                ))

            result += [nodes.definition_list('', *dl_items)]
        
        if route_options.get:
            query_param_fields = []
            for field in input_descriptor.field:
                if field.json_name not in route_params:
                    query_param_fields.append(field)
            
            if query_param_fields:
                dl_items = []
                for field in query_param_fields:
                    field_symbol = descriptor.input_type + '.' + field.name

                    comment_node = nodes.section()
                    comment = find_comment(field_symbol, prefix='')
                    if comment:
                        for child in produce_nodes(self.state, comment):
                            comment_node += child

                    dl_items.append(nodes.definition_list_item(
                        '',
                        nodes.term('', '', nodes.literal('', field.json_name)),
                        nodes.definition('', comment_node),
                    ))
                result += [
                    nodes.rubric('', 'Query Parameters'),
                    nodes.definition_list('', *dl_items),
                ]

        return result


def path_to_symbol(file, path):
    items = iter(path)
    relto = file
    reltype = 'file'
    symbol = '.' + file.package
    for item in items:
        if reltype == 'file':
            if item == 4:  # Message
                idx = next(items)
                reltype, relto = 'message', relto.message_type[idx]
                symbol += '.' + relto.name
            elif item == 5:  # Enum
                idx = next(items)
                reltype, relto = 'enum', relto.enum_type[idx]
                symbol += '.' + relto.name
            elif item == 6:  # Service
                idx = next(items)
                reltype, relto = 'service', relto.service[idx]
                symbol += '.' + relto.name
            elif item == 8:  # FileOptions
                pass
            elif item == 9:  # SourceCodeInfo
                pass
            else:
                raise Exception('Unexpected item {}'.format(item))
        elif reltype == 'message':
            if item == 1:  # Name
                pass
            elif item == 2:  # Field
                idx = next(items)
                reltype, relto = 'field', relto.field[idx]
                symbol += '.' + relto.name
            elif item == 3:  # Nested Type
                idx = next(items)
                reltype, relto = 'message', relto.nested_type[idx]
                symbol += '.' + relto.name
            elif item == 4:  # Enum
                idx = next(items)
                reltype, relto = 'enum', relto.enum_type[idx]
                symbol += '.' + relto.name
            elif item == 5:  # Extension Range
                pass
            else:
                raise Exception('Unexpected item {}'.format(item))
        elif reltype == 'enum':
            if item == 2:  # Value
                idx = next(items)
                symbol += '.' + relto.value[idx].name
            else:
                raise Exception('Unexpected item {}'.format(item))
        elif reltype == 'service':
            if item == 2:  # Method
                idx = next(items)
                symbol += '.' + relto.method[idx].name
            else:
                raise Exception('Unexpected item {}'.format(item))

    return symbol


def find_comment(symbol, indent='', prefix='// '):
    if symbol in comments_by_symbol:
        comment = comments_by_symbol[symbol]
        buf = ''
        for line in comment.split('\n'):
            trimmed = line.rstrip()
            buf += indent + prefix + trimmed + '\n'
        return buf
    return None


def describe_enum(symbol, indent=''):
    descriptor = descriptors_by_symbol[symbol]
    buf = indent + 'enum ' + descriptor.name + ' {\n'
    for value in descriptor.value:
        comment = find_comment(symbol + '.' + value.name, indent=indent + '  ')
        if comment:
            buf += '\n'
            buf += comment
        buf += indent + '  ' + value.name + ' = "' + value.name + '",\n'
    buf += indent + '}\n'
    return buf


def describe_field_type(field):
    if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_BOOL:
        return 'boolean'
    if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_BYTES:
        return 'string'  # Base64
    if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_DOUBLE:
        return 'number'
    if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_ENUM:
        return message_name(field.type_name)
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_FLOAT:
        return 'number'
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_INT32:
        return 'number'
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_INT64:
        return 'string'  # Decimal string
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE:
        nested_type = descriptors_by_symbol[field.type_name]
        if nested_type.options.map_entry:
            key_type = describe_field_type(nested_type.field[0])
            value_type = describe_field_type(nested_type.field[1])
            return '{[key: ' + key_type + ']: ' + value_type + '}'
        if field.type_name == '.google.protobuf.Timestamp':
            return 'string'
        else:
            return message_name(field.type_name)
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_SINT32:
        return 'number'
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_SINT64:
        return 'string'  # Decimal string
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_STRING:
        return 'string'
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_UINT32:
        return 'number'
    elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_UINT64:
        return 'string'  # Decimal string
    else:
        raise Exception('Unexpected field type {}'.format(field.type))


def describe_message(symbol, indent='', related=False, excluded_fields=None):
    descriptor = descriptors_by_symbol[symbol]
    buf = ''

    comment = find_comment(symbol, indent=indent)
    if comment:
        buf += comment

    buf += 'interface ' + descriptor.name + ' {\n'
    for field in descriptor.field:
        if field.json_name in (excluded_fields or []):
            continue

        comment = find_comment(symbol + '.' + field.name, indent=indent + '  ')
        if comment:
            buf += '\n'
            buf += comment
        buf += indent + '  ' + field.json_name + ': '
        buf += describe_field_type(field)

        is_array = (field.label == field.LABEL_REPEATED)

        if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE:
            nested_type = descriptors_by_symbol[field.type_name]
            if nested_type.options.map_entry:
                value_field = nested_type.field[1]
                is_array = False
        
        if is_array:
            buf += '[]'
        
        buf += ';'

        if field.type == descriptor_pb2.FieldDescriptorProto.TYPE_BYTES:
            buf += '  // Base64'
        elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_MESSAGE:
            if field.type_name == '.google.protobuf.Timestamp':
                buf += '  // RFC 3339'
        elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_INT64:
            buf += '  // String decimal'
        elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_UINT64:
            buf += '  // String decimal'
        elif field.type == descriptor_pb2.FieldDescriptorProto.TYPE_SINT64:
            buf += '  // String decimal'

        buf += '\n'
    buf += '}\n'
    return buf


def message_name(symbol):
    return symbol[symbol.rfind('.') + 1 :]


def parse_protobin(app):
    with open(app.config.proto_bin, 'rb') as f:
        data = f.read()

    proto = descriptor_pb2.FileDescriptorSet()
    proto.ParseFromString(data)

    for file in proto.file:
        for service in file.service:
            symbol = '.{}.{}'.format(file.package, service.name)
            descriptors_by_symbol[symbol] = service
            for method_type in service.method:
                descriptors_by_symbol[symbol + '.' + method_type.name] = method_type

        for message_type in file.message_type:
            symbol = '.{}.{}'.format(file.package, message_type.name)
            package_by_symbol[symbol] = file.package
            descriptors_by_symbol[symbol] = message_type
            for enum_type in message_type.enum_type:
                package_by_symbol[symbol] = file.package
                descriptors_by_symbol[symbol + '.' + enum_type.name] = enum_type
            for nested_type in message_type.nested_type:
                package_by_symbol[symbol] = file.package
                descriptors_by_symbol[symbol + '.' + nested_type.name] = nested_type

        for enum_type in file.enum_type:
            symbol = '.{}.{}'.format(file.package, enum_type.name)
            package_by_symbol[symbol] = file.package
            descriptors_by_symbol[symbol] = enum_type
        
        for location in file.source_code_info.location:
            if location.HasField('leading_comments'):
                symbol = path_to_symbol(file, location.path)
                comments_by_symbol[symbol] = location.leading_comments.rstrip()


def setup(app):
    app.add_config_value('proto_bin', 'yamcs-api.protobin', 'env')
    app.add_directive('proto', ProtoDirective)
    app.add_directive('rpc', RPCDirective)
    app.add_directive('route', RouteDirective)
    app.add_directive('service', ServiceDirective)
    app.add_directive('websocket', WebSocketDirective)
    app.add_lexer('uritemplate', lexers.URITemplateLexer)
    app.add_lexer('urivariable', lexers.URIVariableLexer)
    app.connect('builder-inited', parse_protobin)
