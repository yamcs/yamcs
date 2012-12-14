#!/bin/sh
#get rid of some passwords
#cat ../etc/archive.properties | sed s/hrdp.index.password=.*$/hrdp.index.password=abracadabra/ >archive.properties
#cat ../etc/cmdhistory.properties | sed s/cmdhistory.dbPassword=.*$/cmdhistory.dbPassword=abracadabra/ >cmdhistory.properties
#cat ../etc/MDB.properties | sed s/mdbconfig.password==.*$/mdbconfig.password==abracadabra/ >MDB.properties

for i in MDBSequenceContainerCreation 
do
  if [ images/$i.svg -ot images/$i.dia ] 
  then
    dia -e images/$i.svg images/$i.dia
    cat images/$i.svg |sed "s/font-size: 0.8; font-family: sans/font-size: 0.55; font-family: Helvetica/g" >images/$i.tmp.svg; mv images/$i.tmp.svg images/$i.svg 
    cat images/$i.svg |sed "s/font-size: 1; font-family: sans/font-size: 0.8; font-family: Helvetica/g" >images/$i.tmp.svg; mv images/$i.tmp.svg images/$i.svg 
  fi
done

xsltproc --stringparam fop1.extensions 1\
         --stringparam paper.type A4\
         --stringparam section.autolabel 1\
	 --stringparam section.label.includes.component.label 1 \
	 --stringparam ulink.footnotes 1\
	 --stringparam body.start.indent 0pt\
	 --stringparam title.color brown\
     --param marker.section.level 2\
	 --xinclude mydocbook.xsl \
     yamcs-manual.xml >yamcs-manual.fo
          #/tmp/docbook5-xsl-1.72.0/fo/docbook.xsl smallsize.xsl ~nm/USOC/trunk/doc/yamcs-manual.xml >yamcs-manual.fo
fop yamcs-manual.fo yamcs-manual.pdf
#rm archive.properties cmdhistory.properties MDB.properties
