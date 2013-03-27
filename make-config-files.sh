#!/bin/bash
for file in `ls yamcs-core/etc/*.sample`; do cp --no-clobber "${file}" "${file%.*}";  done
