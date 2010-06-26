/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.corespi.se;

import java.io.IOException;
import java.net.URL;
import java.util.Enumeration;

import org.apache.webbeans.corespi.scanner.AbstractMetaDataDiscovery;
import org.apache.webbeans.util.WebBeansUtil;
import org.scannotation.ClasspathUrlFinder;

public class DefaultScannerService extends AbstractMetaDataDiscovery
{
    public DefaultScannerService()
    {
        super();
    }

    protected void configure() throws Exception
    {
        configureAnnotationDB();
    }

    private void configureAnnotationDB() throws Exception
    {
        ClassLoader loader = WebBeansUtil.getCurrentClassLoader();

        URL[] urls = ClasspathUrlFinder.findResourceBases("META-INF/beans.xml", loader);
        
        this.getAnnotationDB().scanArchives(urls);

        configureXML();

    }

    private void configureXML() throws Exception
    {
        try
        {
            Enumeration<URL> resources = getClass().getClassLoader().getResources("META-INF/beans.xml");

            while (resources.hasMoreElements())
            {
                URL resource = resources.nextElement();
                addWebBeansXmlLocation(resource);
            }

        }
        catch (IOException e)
        {
            throw e;
        }
    }


}