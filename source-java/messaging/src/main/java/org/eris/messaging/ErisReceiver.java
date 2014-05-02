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
 * Represents a logical <i>Link</i> for receiving messages from a message-source
 * within the remote peer.
 * 
 * <h4>Capacity And CreditMode</h4>
 * <b><i>Capacity</i></b> and <b><i>CreditMode</i></b> determines if and when
 * message credits are issued and whether or not messages should be prefetched.
 * 
 * <b><i>Capacity</i></b> is loosely defined as the upper limit for the number
 * of messages in the receiver's local queue at any given time. This is not a
 * hard limit. If the capacity is changed while messages are in flight the local
 * queue may end up with more messages than desired. Applications need to keep
 * this in mind.
 * 
 * <h4>How To Set CreditMode</h4>
 * The CreditMode can be specified using
 * {@link ErisSession#createReceiver(String, ReceiverMode, CreditMode)}. If
 * {@link ErisSession#createReceiver(String, ReceiverMode)} is used instead,
 * CreditMode defaults to {@link CreditMode#AUTO}
 * 
 * <h4>How To Set Capacity</h4>
 * When the ErisReceiver is created the default capacity is determined based on it's
 * CreditMode. <br>
 * If {@link CreditMode#AUTO} is used it will be set to "1". This can be changed
 * via the system property "eris.receiver.capacity". <br>
 * If {@link CreditMode#EXPLICT} is used it will be set to "0".
 * 
 * Once the ErisReceiver is created it can explicitly specify it's capacity using
 * {@link ErisReceiver#setCapacity(int)}. The capacity can be any non negative
 * integer including zero.
 * 
 * <h4>How ErisMessage Credits Work</h4>
 * When the ErisReceiver is created, <br>
 * If CreditMode is {@link CreditMode#AUTO}, "N" message credits will be issued
 * immediately, where "N" is the default capacity as determined above. <br>
 * If CreditMode is {@link CreditMode#EXPLICT} no message credits are issued.
 * 
 * When the application sets the capacity using
 * {@link ErisReceiver#setCapacity(int)}, the library will issue a cancel for any
 * previously issued credits and re-issue credits as specified by the method. If
 * any messages were in flight before the peer sees the 'cancel' the receiver
 * will end up getting extra messages than intended.
 * 
 * If CreditMode is {@link CreditMode#AUTO}, the library will automatically
 * re-issue credits after a certain number of messages have been marked as
 * either accepted, rejected or released by the application. The library will
 * determine the optimum threshold for when the re-issue happens.
 * 
 * If CreditMode is {@link CreditMode#EXPLICT} the application needs to
 * explicitly manage it's message credit and use
 * {@link ErisReceiver#setCapacity(int)} to issue credits when it is ready to
 * process messages.
 * 
 * <h4>Prefetch</h4>
 * If CreditMode is {@link CreditMode#AUTO}, the library will <i>prefetch</i> N
 * messages from the peer (if available) when the ErisReceiver is created. Where N
 * is default capacity. To <b><i>disable prefetch</b></i>, you need to set
 * capacity to zero. The library will then attempt to receive messages on demand
 * when any of the receive methods are called.<br>
 * 
 * If CreditMode is {@link CreditMode#EXPLICT} no messages are prefetched.
 * ErisMessage delivery is started only when the application explicitly issues
 * credits.
 * 
 * <h4>Exceptions</h4>
 * <ul>
 * <li>TransportException : Thrown when the underlying transport fails.</li>
 * <li>ReceiverException  : Thrown when the ErisReceiver gets to an erroneous state.</li>
 * <li>TimeoutException   : Thrown when an operation exceeds the connection timeout.</li>
 * </ul>
 */
public interface ErisReceiver
{
    /**
     * @return The address used for establishing the Link
     */
    String getAddress();

    /**
     * @return The CreditMode used by this ErisReceiver
     * @see CreditMode
     */
    CreditMode getCreditMode();

    /**
     * @return The current capacity for this ErisReceiver.
     * @see ErisReceiver#setCapacity(int)
     */
    int getCapacity();

    /**
     * @return The number of messages available in the receivers local queue.
     */
    int getAvailable();

    /**
     * @return The number of messages received by the application but has not
     *         yet been settled.
     */
    int getUnsettled();

    /**
     * 
     * @return Returns the next available message in it's local queue. If there
     *         are no messages in the local queue, it will block until one
     *         becomes available or the Link is closed.
     * 
     *         If CreditMode is AUTO & capacity is zero, the library will issue
     *         a single message credit. If CreditMode is EXPLICIT & capacity is
     *         zero an exception will be thrown.
     * 
     * @throws ReceiverException
     *             A ReceiverException will be thrown if the ErisReceiver was closed
     *             due to an error.
     * 
     * @throws TransportException
     *             A TransportException will be thrown if the underlying
     *             transport fails,
     */
    ErisMessage receive() throws TransportException, ReceiverException;

    /**
     * 
     * @return Returns the next available message in it's local queue. If there
     *         are no messages in the local queue, it will block until one
     *         becomes available within the specified timeout or the Link is
     *         closed.
     * 
     *         If CreditMode is AUTO & capacity is zero, the library will issue
     *         a single message credit. If CreditMode is EXPLICIT & capacity is
     *         zero an exception will be thrown.
     * 
     * @throws ReceiverException
     *             A ReceiverException will be thrown if the ErisReceiver was closed
     *             due to an error.
     * 
     * @throws TransportException
     *             A TransportException will be thrown if the underlying
     *             transport fails.
     * 
     * @throws TimeoutException
     *             A TimeoutException will be thrown if no message was available
     *             within the given timeout.
     */
    ErisMessage receive(long timeout) throws TransportException, ReceiverException, TimeoutException;

    /**
     * Sets the capacity for this ErisReceiver. The capacity should be a non
     * negative value.
     * 
     * If CreditMode is EXPLICIT, the application needs to set the capacity to a
     * non zero value before calling any of the receive methods. Else an
     * exception will be thrown.
     * 
     */
    void setCapacity(int credits) throws TransportException, ReceiverException;

    /**
     * Close the Link and free any resources associated with it.
     */
    void close() throws TransportException;
}