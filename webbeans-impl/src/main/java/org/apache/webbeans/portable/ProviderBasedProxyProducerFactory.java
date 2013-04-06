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
package org.apache.webbeans.portable;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.Producer;
import javax.inject.Provider;

import org.apache.webbeans.config.WebBeansContext;
import org.apache.webbeans.container.ProducerFactory;
import org.apache.webbeans.util.Asserts;

public class ProviderBasedProxyProducerFactory<T> implements ProducerFactory<T>
{

    private Provider<T> provider;
    private Class<T> providerType;
    private WebBeansContext webBeansContext;
    
    public ProviderBasedProxyProducerFactory(Provider<T> provider, Class<T> providerType, WebBeansContext context)
    {
        Asserts.assertNotNull(provider);
        Asserts.assertNotNull(providerType);
        Asserts.assertNotNull(context);
        this.provider = provider;
        this.providerType = providerType;
        this.webBeansContext = context;
    }

    @Override
    public Producer<T> createProducer(Bean<T> bean)
    {
        return new ProviderBasedProxyProducer<T>(webBeansContext, providerType, provider);
    }
}