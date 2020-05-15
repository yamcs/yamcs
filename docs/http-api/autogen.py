import os
import re
import sys
from pathlib import Path

from google.protobuf import descriptor_pb2

from sphinx.util.osutil import FileAvoidWrite
from sphinx.util.template import ReSTRenderer
from yamcs.api import annotations_pb2


def camel_to_slug(name, sep='-', lower=True):
    name = re.sub('(.)([A-Z][a-z]+)', r'\1' + sep + r'\2', name)
    name = re.sub('([a-z0-9])([A-Z])', r'\1' + sep + r'\2', name)
    return name.lower() if lower else name


def titlecase(text):
    return camel_to_slug(text, sep=' ', lower=False)


def replace(a, b, c):
    return a.replace(b, c)


class YamcsReSTRenderer(ReSTRenderer):
    def __init__(self):
        super().__init__('templates')
        self.env.filters['slug'] = camel_to_slug
        self.env.filters['replace'] = replace
        self.env.filters['titlecase'] = titlecase


def create_service_file(symbol, service, filename):
    context = {
        "symbol": symbol,
        "service": service,
    }
    text = YamcsReSTRenderer().render('service.rst_t', context)
    with FileAvoidWrite(filename) as f:
        f.write(text)


def create_route_file(symbol, method, filename):
    context = {
        "symbol": symbol,
        "method": method,
        "route_options": method.options.Extensions[annotations_pb2.route],
    }
    text = YamcsReSTRenderer().render('route.rst_t', context)
    with FileAvoidWrite(filename) as f:
        f.write(text)


def create_websocket_file(symbol, method, filename):
    context = {
        "symbol": symbol,
        "method": method,
        "websocket_options": method.options.Extensions[annotations_pb2.websocket],
    }
    text = YamcsReSTRenderer().render('websocket.rst_t', context)
    with FileAvoidWrite(filename) as f:
        f.write(text)


if __name__ == '__main__':
    destdir = Path(sys.argv[1])
    destdir.mkdir(exist_ok=True)

    with open('yamcs-api.protobin', 'rb') as f:
        data = f.read()

    proto = descriptor_pb2.FileDescriptorSet()
    proto.ParseFromString(data)
    service_links = []
    for file in proto.file:
        for service in file.service:
            servicedir = Path(destdir, camel_to_slug(service.name).replace('-api', ''))
            servicedir.mkdir(exist_ok=True)

            servicefile = os.path.join(servicedir, 'index.rst')
            symbol = '.' + file.package + '.' + service.name
            create_service_file(symbol, service, servicefile)
            service_links.append(servicedir.name + '/index')

            for method in service.method:
                filename = camel_to_slug(method.name) + '.rst'
                methodfile = os.path.join(servicedir, filename)
                symbol = '.' + file.package + '.' + service.name + '.' + method.name
                
                if method.options.HasExtension(annotations_pb2.route):
                    create_route_file(symbol, method, methodfile)
                elif method.options.HasExtension(annotations_pb2.websocket):
                    create_websocket_file(symbol, method, methodfile)

    service_links.sort()
    text = YamcsReSTRenderer().render('index.rst_t', {
        "service_links": service_links,
    })
    with FileAvoidWrite(os.path.join(destdir, 'index.rst')) as f:
        f.write(text)
