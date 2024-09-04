from xml.etree import ElementTree as ET

tree = ET.ElementTree()
tree.parse("../../pom.xml")
yamcs_version_el = tree.getroot().find("{http://maven.apache.org/POM/4.0.0}version")

project = "Yamcs"
copyright = "2006-present, Space Applications Services"
author = "Yamcs Team"
version = yamcs_version_el.text
release = version
source_suffix = ".rst"
language = "en"
exclude_patterns = ["_build", "Thumbs.db", ".DS_Store"]
pygments_style = "sphinx"

extensions = [
    "sphinx.ext.extlinks",
    "sphinxcontrib.fulltoc",
    "sphinxcontrib.yamcs",
]

# Force-disable conversion of -- to en-dash
smartquotes = False

extlinks = {
    "source": ("https://github.com/yamcs/yamcs/blob/master/%s", "GitHub: %s"),
}

html_theme = "nature"
html_theme_options = {
    "sidebarwidth": "300px",
}
html_show_sourcelink = False

latex_elements = {
    "papersize": "a4paper",
    "figure_align": "htbp",
    "extraclassoptions": "openany",
}

# Grouping the document tree into LaTeX files. List of tuples
# (source start file, target name, title,
#  author, documentclass [howto, manual, or own class]).
latex_documents = [
    (
        "index",
        "yamcs-http-api.tex",
        "Yamcs HTTP API",
        "Space Applications Services",
        "manual",
    ),
]

latex_show_pagerefs = True

latex_show_urls = "footnote"

yamcs_api_protobin = (
    "../../yamcs-api/target/generated-resources/protobuf/yamcs-api.protobin"
)
yamcs_api_destdir = "."
yamcs_api_title = "Yamcs HTTP API"
yamcs_api_additional_docs = [
    "overview.rst",
    "filtering.rst",
    "partial-responses.rst",
    "websocket.rst",
]
