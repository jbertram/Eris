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

import org.eris.Messaging;

/**
 * Represents a logical <i>Link</i> for sending messages to a destination within
 * the remote peer.
 * 
 * <h4>Creating message to send</h4> The application can use
 * {@link Messaging#message()} to create a message that can be used or sending.
 * To set the content use {@link ErisMessage#setContent(Object)}.
 * 
 * <h4>Tracking message delivery</h4> If the SenderMode is
 * {@link #AT_LEAST_ONCE} the application has two options for tracking
 * messages. <br>
 * 
 * <ul>
 * <li>Synchronously track messages by using {@link Tracker#awaitSettlement()}
 * 
 * <pre>
 * Ex:  
 * {@code sender.send(msg).awaitSettlement();} or <br>
 * {@code         
 * Tracker tracker = sender.send(msg);
 * .....
 * tracker.awaitSettlement();
 * }
 * </pre>
 * 
 * </li>
 * <li>Receive completions asynchronously by registering a
 * {@link CompletionListener} with the ErisSession using
 * {@link ErisSession#setCompletionListener(CompletionListener)}</li>
 * </ul>
 * 
 * <h4>Exceptions</h4>
 * <ul>
 * <li>TransportException : Thrown when the underlying transport fails.</li>
 * <li>SenderException    : Thrown when the ErisSender gets to an erroneous state.</li>
 * <li>TimeoutException   : Thrown when an operation exceeds the connection timeout.</li>
 * </ul>
 */
public interface ErisSender
{
    /**
     * The address used for establishing the Link
     */
    String getAddress();

    /**
     * Provides a hint to the peer about the availability of messages.
     */
    void offerCredits(int credits) throws SenderException, TransportException;

    /**
     * Outstanding messages deliveries that the peer has not yet confirmed as
     * settled.
     */
    int getUnsettled() throws SenderException, TransportException;

    /**
     * 
     * @param msg {@link ErisMessage} to be sent. The application can use
     *        {@link Messaging#message()} to create a message that can be used
     *        for sending.
     * 
     * @return A {@link Tracker} object that can be used to track the status of
     *         the message delivery.
     * 
     * @throws ReceiverException
     *             A ReceiverException will be thrown if the ErisSender was closed
     *             due to an error.
     * 
     * @throws TransportException
     *             A TransportException will be thrown if the underlying
     *             transport fails during the send.
     */
    Tracker send(ErisMessage msg) throws SenderException, TransportException;

    /**
     * Close the Link and free any resources associated with it.
     */
    void close() throws TransportException;
}