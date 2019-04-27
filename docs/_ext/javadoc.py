from docutils import nodes, utils


def javadoc_role(role, rawtext, text, lineno, inliner, options={}, content=[]):
    print_short_name = text.startswith('~')
    if print_short_name:
        text = text[1:]
        idx = text.rindex('.')
        label = text[(idx + 1):]
    else:
        label = text

    ref = 'https://www.yamcs.org/yamcs/javadoc/index.html?%s.html' % text.replace('.', '/')
    node = nodes.reference(rawtext, utils.unescape(label), refuri=ref, **options)
    return [node], []


def setup(app):
    app.add_role('javadoc', javadoc_role)
