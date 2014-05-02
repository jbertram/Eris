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
package org.eris.messaging.amqp.proton;

import java.nio.ByteBuffer;

import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.message.Message;
import org.eris.messaging.ErisMessage;
import org.eris.messaging.ErisSender;
import org.eris.messaging.SenderException;
import org.eris.messaging.Tracker;
import org.eris.messaging.TransportException;

public class ErisSenderImpl implements ErisSender
{
    private String _address;
    private ErisSessionImpl _erisSession;
    private Sender _protonSender;
    private boolean _dynamic = false;

    ErisSenderImpl(String address, ErisSessionImpl ssn, Sender sender)
    {
        _address = address;
        _erisSession = ssn;
        _protonSender = sender;
    }

    @Override
    public String getAddress()
    {
        return _address;
    }

    @Override
    // Need to handle buffer overflows
    public Tracker send(ErisMessage msg) throws SenderException, TransportException
    {
        if (_protonSender.getLocalState() == EndpointState.CLOSED || _protonSender.getRemoteState() == EndpointState.CLOSED)
        {
            throw new SenderException("ErisSender closed");
        }

        if (msg instanceof ErisMessageImpl)
        {
            byte[] tag = longToBytes(_erisSession.getNextDeliveryTag());
            Delivery delivery = _protonSender.delivery(tag);
            TrackerImpl tracker = new TrackerImpl(_erisSession);
            delivery.setContext(tracker);
            if (_protonSender.getSenderSettleMode() == SenderSettleMode.SETTLED)
            {
                delivery.settle();
                tracker.markSettled();
            }

            Message m = ((ErisMessageImpl) msg).getProtocolMessage();
            if (m.getAddress() == null)
            {
                m.setAddress(_address);
            }
            byte[] buffer = new byte[1024];
            int encoded = m.encode(buffer, 0, buffer.length);
            _protonSender.send(buffer, 0, encoded);
            _protonSender.advance();
            _erisSession.write();
            return tracker;
        }
        else
        {
            throw new SenderException("Unsupported message implementation");
        }
    }

    private byte[] longToBytes(final long value)
    {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.putLong(value);
        return buffer.array();
    }

    @Override
    public void offerCredits(int credits) throws SenderException, TransportException
    {
        checkPreConditions();
        _protonSender.offer(credits);
        _erisSession.write();
    }

    @Override
    public int getUnsettled() throws SenderException
    {
        checkPreConditions();
        return _protonSender.getUnsettled();
    }

    @Override
    public void close() throws TransportException
    {
        _erisSession.closeLink(_protonSender);
    }

    void checkPreConditions() throws SenderException
    {
        if (_protonSender.getLocalState() != EndpointState.ACTIVE)
        {
            throw new SenderException("ErisSender is closed");
        }
    }

    void setAddress(String addr)
    {
        _address = addr;
    }

    void setDynamicAddress(boolean b)
    {
        _dynamic = b;
    }

    boolean isDynamicAddress()
    {
        return _dynamic;
    }
}