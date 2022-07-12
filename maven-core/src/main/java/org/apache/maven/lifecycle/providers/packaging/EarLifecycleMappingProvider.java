package org.apache.maven.lifecycle.providers.packaging;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

/**
 * {@code ear} packaging plugins bindings provider for {@code default} lifecycle.
 */
@Named( "ear" )
@Singleton
public final class EarLifecycleMappingProvider
    extends AbstractLifecycleMappingProvider
{
    // START SNIPPET: ear
    @SuppressWarnings( "checkstyle:linelength" )
    private static final String[] BINDINGS =
    {
        "generate-resources", "org.apache.maven.plugins:maven-ear-plugin:" + EAR_PLUGIN_VERSION + ":generate-application-xml",
        "process-resources",  "org.apache.maven.plugins:maven-resources-plugin:" + RESOURCES_PLUGIN_VERSION + ":resources",
        "package",            "org.apache.maven.plugins:maven-ear-plugin:" + EAR_PLUGIN_VERSION + ":ear",
        "install",            "org.apache.maven.plugins:maven-install-plugin:" + INSTALL_PLUGIN_VERSION + ":install",
        "deploy",             "org.apache.maven.plugins:maven-deploy-plugin:" + DEPLOY_PLUGIN_VERSION + ":deploy"
    };
    // END SNIPPET: ear

    @Inject
    public EarLifecycleMappingProvider()
    {
        super( BINDINGS );
    }
}
