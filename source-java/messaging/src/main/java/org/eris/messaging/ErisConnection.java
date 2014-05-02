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
package org.eris.messaging;

/**
 * 
 * Represents a logical <i>ErisConnection</i> to a remote peer within a messaging
 * network.
 * 
 * <h4>Exceptions</h4>
 * <ul>
 * <li>TransportException : Thrown when the underlying transport fails.</li>>
 * <li>ReceiverException  : Thrown when the ErisConnection gets to an erroneous state.</li>
 * <li>TimeoutException   : Thrown when an operation exceeds the connection timeout.</li>
 * </ul>
 * 
 * ErisConnection timeout defaults to 60 secs. This value can be changed via the
 * <i>"eris.connection.timeout"</i> system property, or by providing an
 * application specific ConnectionSettings implementation when creating the
 * ErisConnection object.
 * 
 * @see ConnectionSettings
 */
public interface ErisConnection
{
    /**
     * Creates the underlying physical connection to the peer.
     */
    public void connect() throws TransportException, ConnectionException, TimeoutException;

    /**
     * Establishes a logical ErisSession for exchanging of messages.
     */
    public ErisSession createSession() throws TransportException, ConnectionException, TimeoutException;

    /**
     * Terminates the ErisConnection and free any resources associated with this
     * ErisConnection. If there are any active sessions, it will close them first
     * before closing the ErisConnection.
     */
    public void close() throws TransportException, ConnectionException, TimeoutException;
}
