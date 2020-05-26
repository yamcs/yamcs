SPHINXOPTS =
SPHINXBUILD = sphinx-build
SOURCEDIR = _auto
BUILDDIR = _build
PYTHON = python

# Put it first so that "make" without argument is like "make help".
help:
	@$(SPHINXBUILD) -M help "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

clean:
	@echo Removing everything under \'$(SOURCEDIR)\'...
	@rm -rf $(SOURCEDIR)/*
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

autogen:
	@echo Generating automated sections
	@mkdir -p $(SOURCEDIR)
	@cp conf.py *.rst $(SOURCEDIR)
	@$(PYTHON) autogen.py $(SOURCEDIR)

html: autogen Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

json: autogen Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

latexpdf: autogen Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)

# Catch-all target: route all unknown targets to Sphinx using the new
# "make mode" option.  $(O) is meant as a shortcut for $(SPHINXOPTS).
%: Makefile
	@$(SPHINXBUILD) -M $@ "$(SOURCEDIR)" "$(BUILDDIR)" $(SPHINXOPTS) $(O)


.PHONY: autogen help html json latexpdf Makefile
