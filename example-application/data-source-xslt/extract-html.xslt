<?xml version="1.0"?>

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0">

    <xsl:output indent="yes" />

    <xsl:template match="/">
        <xsl:copy-of select="//body">
        </xsl:copy-of>
    </xsl:template>
</xsl:stylesheet>
