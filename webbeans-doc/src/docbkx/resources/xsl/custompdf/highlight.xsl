<?xml version="1.0" encoding="UTF-8"?>
    <!--
        Licensed to the Apache Software Foundation (ASF) under one or more
        contributor license agreements. See the NOTICE file distributed with
        this work for additional information regarding copyright ownership. The
        ASF licenses this file to you under the Apache License, Version 2.0 (the
        "License"); you may not use this file except in compliance with the
        License. You may obtain a copy of the License at

        http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable
        law or agreed to in writing, software distributed under the License is
        distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
        KIND, either express or implied. See the License for the specific
        language governing permissions and limitations under the License.
    -->
<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format" 
    xmlns:xslthl="http://xslthl.sf.net" 
    exclude-result-prefixes="xslthl" 
    version="1.0">
    	
        <xsl:template match='xslthl:keyword'>
        <fo:inline font-weight="bold">
            <xsl:apply-templates />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:string'>
        <fo:inline font-weight="bold" font-style="italic">
            <xsl:apply-templates />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:comment'>
        <fo:inline font-style="italic" color="green">
            <xsl:apply-templates />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:tag'>
        <fo:inline font-weight="bold" color="Maroon">
            <xsl:apply-templates />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:attribute'>
        <fo:inline color="red">
            <xsl:apply-templates />
        </fo:inline>
    </xsl:template>

    <xsl:template match='xslthl:value'>
        <fo:inline color="black">
            <xsl:apply-templates />
        </fo:inline>
    </xsl:template>


</xsl:stylesheet>
