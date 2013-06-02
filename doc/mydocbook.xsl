<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:d="http://docbook.org/ns/docbook"
		xmlns:exsl="http://exslt.org/common"
                xmlns:fo="http://www.w3.org/1999/XSL/Format"
                xmlns:ng="http://docbook.org/docbook-ng"
                xmlns:db="http://docbook.org/ns/docbook"
                exclude-result-prefixes="db ng exsl d"
                version='1.0'>

<xsl:import href="/usr/share/xml/docbook/stylesheet/nwalsh/fo/docbook.xsl"/>

<xsl:param name="region.before.extent">0.9in</xsl:param>
<xsl:param name="body.margin.top">0.9in</xsl:param>

<xsl:param name="title.color">brown</xsl:param>
<xsl:param name="titlepage.color">brown</xsl:param>
<xsl:param name="chapter.title.color">brown</xsl:param>
<xsl:param name="section.title.color">brown</xsl:param>



<xsl:attribute-set name="component.title.properties">
  <xsl:attribute name="font-size">16pt</xsl:attribute>
</xsl:attribute-set>


<xsl:attribute-set name="section.title.level1.properties">
  <xsl:attribute name="font-size">14pt</xsl:attribute>
</xsl:attribute-set>


<xsl:attribute-set name="section.title.level2.properties">
  <xsl:attribute name="font-size">12pt</xsl:attribute>
</xsl:attribute-set>


<xsl:attribute-set name="section.title.level3.properties">
  <xsl:attribute name="font-size">12pt</xsl:attribute>
</xsl:attribute-set>


<xsl:template match="d:screen">
  <fo:block font-size="8pt">
    <xsl:apply-imports/>
  </fo:block>
</xsl:template>


<xsl:template match="d:bookinfo/d:pubdate" mode="titlepage.mode" priority="2">
  <fo:block>
    <xsl:call-template name="gentext">
      <xsl:with-param name="key" select="'pubdate'"/>
    </xsl:call-template>
    <xsl:text></xsl:text>
    <xsl:apply-templates mode="titlepage.mode"/>
  </fo:block>
  <xsl:value-of select="ancestor-or-self::d:book/d:bookinfo/d:edition"/>
</xsl:template>









<xsl:template name="header.content">
  <xsl:param name="pageclass" select="''"/>
  <xsl:param name="sequence" select="''"/>
  <xsl:param name="position" select="''"/>
  <xsl:param name="gentext-key" select="''"/>

<!--
  <fo:block>
    <xsl:value-of select="$pageclass"/>
    <xsl:text>, </xsl:text>
    <xsl:value-of select="$sequence"/>
    <xsl:text>, </xsl:text>
    <xsl:value-of select="$position"/>
    <xsl:text>, </xsl:text>
    <xsl:value-of select="$gentext-key"/>
  </fo:block>
-->

  <fo:block>

    <!-- sequence can be odd, even, first, blank -->
    <!-- position can be left, center, right -->
    <xsl:choose>
      <xsl:when test="$sequence = 'blank'">
        <!-- nothing -->
      </xsl:when>

      <xsl:when test="$position='left'">

         <fo:external-graphic content-height="2em" alignment-baseline="baseline" src="images/yamcs-rectangle.png"/>
<!--          <xsl:text> User Manual</xsl:text> -->
       <!-- <xsl:call-template name="draft.text"/> -->
      </xsl:when>

      <xsl:when test="$position='right'">
        <xsl:if test="$pageclass != 'titlepage'">
          <xsl:choose>
            <xsl:when test="ancestor::book and ($double.sided != 0)">
              <fo:retrieve-marker retrieve-class-name="section.head.marker"
                                  retrieve-position="first-including-carryover"
                                  retrieve-boundary="page-sequence"/>
              
            </xsl:when>
            <xsl:otherwise>
            <fo:table table-layout="fixed" width="100%">
                <fo:table-column column-width="17mm"/>
                <fo:table-column column-width="33mm"/>
                <fo:table-body>
                    <fo:table-row>
                        <fo:table-cell text-align='left'> <fo:block>Reference:</fo:block> </fo:table-cell>
                        <fo:table-cell text-align='left'> <fo:block>YAMCS-SA-MA-001</fo:block></fo:table-cell>
                    </fo:table-row>
                    <fo:table-row>
                        <fo:table-cell text-align='left'> <fo:block>Date:</fo:block> </fo:table-cell>
                        <fo:table-cell text-align='left'> <fo:block>19-Feb-2013</fo:block> </fo:table-cell>
                    </fo:table-row>
                    <fo:table-row>
                        <fo:table-cell text-align='left'> <fo:block>Version:</fo:block> </fo:table-cell>
                        <fo:table-cell text-align='left'> <fo:block>1.1.0</fo:block> </fo:table-cell>
                    </fo:table-row>
                </fo:table-body>

            </fo:table>

            </xsl:otherwise>
          </xsl:choose>
        </xsl:if>
      </xsl:when>

      <xsl:when test="$position='center'">
        <fo:block>&#x00A0;</fo:block>
        <fo:block>&#x00A0;</fo:block>
        <fo:block>&#x00A0;</fo:block>
        <xsl:apply-templates select="." mode="object.title.markup"/>

      </xsl:when>

      <xsl:when test="$position='right'">
        <!-- Same for odd, even, empty, and blank sequences -->
        <xsl:call-template name="draft.text"/>
      </xsl:when>

      <xsl:when test="$sequence = 'first'">
        <!-- nothing for first pages -->
      </xsl:when>

      <xsl:when test="$sequence = 'blank'">
        <!-- nothing for blank pages -->
      </xsl:when>
    </xsl:choose>
  </fo:block>
</xsl:template>

</xsl:stylesheet>
