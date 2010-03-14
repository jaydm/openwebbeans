/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.webbeans.boot;

import java.util.Properties;
import java.util.concurrent.CountDownLatch;

import org.apache.webbeans.lifecycle.LifecycleFactory;
import org.apache.webbeans.logger.WebBeansLogger;
import org.apache.webbeans.spi.ContainerLifecycle;

public class Bootstrap
{
    private static final WebBeansLogger logger = WebBeansLogger.getLogger(Bootstrap.class);
    
    private CountDownLatch latch = new CountDownLatch(1);
    
    private ContainerLifecycle containerLifecycle = null;
    
    private Properties properties = null;
    
    public void init(Properties properties)
    {
        logger.info("Initializing OpenWebBeans Container");
        this.containerLifecycle = LifecycleFactory.getInstance().getLifecycle();
    }
    
    public void start() throws Exception
    {
        logger.info("Starting OpenWebBeans Container...");
        long begin = System.currentTimeMillis();
        
        containerLifecycle.startApplication(this.properties);
        Runtime.getRuntime().addShutdownHook(new Thread(){
           
            public void run()
            {
                Bootstrap.this.latch.countDown();
            }
            
        });
        
        logger.info("OpenWebBeans Container has started within " + Long.toString(System.currentTimeMillis() - begin) + " mseconds.");
        this.latch.await();
        
        logger.info("Stopping OpenWebBeans Container...");
        
        containerLifecycle.stopApplication(this.properties);
        
        logger.info("OpenWebBeans Container has stopped.");
    }
    
    public static void main(String []args) throws Exception
    {
        Bootstrap boot = new Bootstrap();
        boot.init(System.getProperties());
    }

}