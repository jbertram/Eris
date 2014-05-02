/*
 *
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
 *
 */
package org.eris.messaging.server;

import java.nio.ByteBuffer;

/**
 * Represents an in-bound connection.
 */
public interface InboundConnection
{
    /**
     * Accepts the network connection and initiate the high level protocol
     * handshake.
     */
    void accept();

    /**
     * Rejects by closing the network connection. This is useful if the
     * application is unable to accept any new connections at this point. It
     * will provide a reason-code if the high level protocol supports it.
     */
    void reject();

    /**
     * Rejects by closing the network connection, but provides an alternative
     * endpoint to connect to if the high level protocol supports it.
     */
    void redirect();

    /**
     * Once you accept the ErisConnection, all state changes are notified via the
     * event Listener.
     */
    void setEventListener();

    /**
     * Orderly shutdown at the high level protocol level. Also closes the
     * underlying network connection.
     */
    void close();
}