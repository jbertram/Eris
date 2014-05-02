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
 * Represents a logical <i>ErisSession</i> for exchanging of messages.
 * 
 * <h4>Exceptions</h4>
 * <ul>
 * <li>TransportException : Thrown when the underlying transport fails.</li>>
 * <li>SessionException   : Thrown when the ErisSession gets to an erroneous state.</li>
 * </ul>
 */
public interface ErisSession
{
    /**
     * Flag for use with {@link ErisSession#accept(ErisMessage, int...)},
     * {@link ErisSession#reject(ErisMessage, int...)} and
     * {@link ErisSession#release(ErisMessage, int...)} methods. When used with the
     * above methods, all messages upto that point will be affected by the given
     * action.
     */
    static final int CUMULATIVE = 0x01;

    /**
     * Establishes a logical Link with the remote peer for sending messages to
     * the specified address.
     * 
     * @param address
     *            The address is an arbitrary string identifying a logical
     *            "destination" within the remote peer, which is capable of
     *            receiving the messages.
     * @param mode
     *            The SenderMode specifies the level of reliability expected by
     *            the application.
     * @see SenderMode
     */
    ErisSender createSender(String address, SenderMode mode) throws TransportException, SessionException;

    /**
     * Establishes a logical Link with the remote peer for receiving messages
     * from the specified address.
     * 
     * @param address
     *            The address is an arbitrary string identifying a logical
     *            "message source" within the remote peer
     * @param mode
     *            The ReceiverMode specifies the level of reliability expected
     *            by the application.
     * @see ReceiverMode
     */
    ErisReceiver createReceiver(String address, ReceiverMode mode) throws TransportException, SessionException;

    /**
     * Establishes a logical Link with the remote peer for receiving messages
     * from the specified address.
     * 
     * @param address
     *            The address is an arbitrary string identifying a logical
     *            "message source" within the remote peer
     * @param mode
     *            The ReceiverMode specifies the level of reliability expected
     *            by the application.
     * @param creditMode
     *            The CreditMode specifies how credit is replenished.
     * @see ReceiverMode
     * @see CreditMode
     */
    ErisReceiver createReceiver(String address, ReceiverMode mode, CreditMode creditMode) throws TransportException,
            SessionException;

    /**
     * Accepts the given message or all messages upto that point if the
     * {@link ErisSession#CUMULATIVE} flag is used.
     */
    void accept(ErisMessage msg, int... flags) throws SessionException;

    /**
     * Rejects the given message or all messages upto that point if the
     * {@link ErisSession#CUMULATIVE} flag is used.
     */
    void reject(ErisMessage msg, int... flags) throws SessionException;

    /**
     * Release the given message or all messages upto that point if the
     * {@link ErisSession#CUMULATIVE} flag is used.
     */
    void release(ErisMessage msg, int... flags) throws SessionException;

    /**
     * The {@link CompletionListener} provides a way to receive message
     * completions asynchronously.
     * 
     * @see CompletionListener
     */
    void setCompletionListener(CompletionListener l) throws SessionException;

    /**
     * Terminates the ErisSession and free any resources associated with this
     * ErisSession. If there are any active Links, it will close them first before
     * closing the ErisSession.
     */
    void close() throws TransportException;
}