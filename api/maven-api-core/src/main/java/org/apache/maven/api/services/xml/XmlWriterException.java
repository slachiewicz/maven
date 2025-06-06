/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.api.services.xml;

import org.apache.maven.api.annotations.Experimental;
import org.apache.maven.api.services.MavenException;

/**
 * An exception thrown while writing an XML file.
 *
 * @since 4.0.0
 */
@Experimental
public class XmlWriterException extends MavenException {

    private final Location location;

    /**
     * @param message the message for the exception
     * @param e the exception itself
     */
    public XmlWriterException(String message, Location location, Exception e) {
        super(message, e);
        this.location = location;
    }

    public Location getLocation() {
        return location;
    }
}
