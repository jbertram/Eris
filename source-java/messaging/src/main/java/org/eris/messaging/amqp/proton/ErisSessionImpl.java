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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.messaging.Source;
import org.apache.qpid.proton.amqp.messaging.Target;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.eris.messaging.CompletionListener;
import org.eris.messaging.CreditMode;
import org.eris.messaging.ErisMessage;
import org.eris.messaging.ErisReceiver;
import org.eris.messaging.ErisSender;
import org.eris.messaging.ErisSession;
import org.eris.messaging.ReceiverMode;
import org.eris.messaging.SenderMode;

public class ErisSessionImpl implements ErisSession
{
    private static final DeliveryState ACCEPTED = Accepted.getInstance();

    private static final DeliveryState REJECTED = new Rejected();

    private static final DeliveryState RELEASED = Released.getInstance();

    private ErisConnectionImpl _conn;

    private Session _protonSession;

    private AtomicLong _deliveryTag = new AtomicLong(0);

    private AtomicLong _incomingSequence = new AtomicLong(0);

    private CompletionListener _completionListener = null;

    private final Map<Sender, ErisSenderImpl> _senders = new ConcurrentHashMap<Sender, ErisSenderImpl>(2);

    private final Map<Receiver, ErisReceiverImpl> _receivers = new ConcurrentHashMap<Receiver, ErisReceiverImpl>(2);

    private final Map<Long, Delivery> _unsettled = new ConcurrentHashMap<Long, Delivery>();

    private final String _id;

    ErisSessionImpl(ErisConnectionImpl conn, Session ssn)
    {
        _id = UUID.randomUUID().toString();
        _conn = conn;
        _protonSession = ssn;
    }

    @Override
    public ErisSender createSender(String address, SenderMode mode)
            throws org.eris.messaging.TransportException, org.eris.messaging.SessionException
    {
        checkPreConditions();
        Sender sender;
        Source source = new Source();
        Target target = new Target();
        if (address == null || address.isEmpty() || address.equals("#"))
        {
            String temp = UUID.randomUUID().toString();
            sender = _protonSession.sender(temp);
            target.setDynamic(true);
        }
        else
        {
            sender = _protonSession.sender(address);
            target.setAddress(address);
        }
        sender.setTarget(target);
        sender.setSource(source);
        sender.setSenderSettleMode(mode == SenderMode.AT_MOST_ONCE ? SenderSettleMode.SETTLED
                : SenderSettleMode.UNSETTLED);
        sender.open();

        ErisSenderImpl senderImpl = new ErisSenderImpl(address, this, sender);
        senderImpl.setDynamicAddress(target.getDynamic());
        _senders.put(sender, senderImpl);
        sender.setContext(senderImpl);
        _conn.write();
        return senderImpl;
    }

    @Override
    public ErisReceiver createReceiver(String address, ReceiverMode mode)
            throws org.eris.messaging.TransportException, org.eris.messaging.SessionException
    {
        return createReceiver(address, mode, CreditMode.AUTO);
    }

    @Override
    public ErisReceiver createReceiver(String address, ReceiverMode mode, CreditMode creditMode)
            throws org.eris.messaging.TransportException, org.eris.messaging.SessionException
    {
        checkPreConditions();
        Receiver receiver;
        Source source = new Source();
        Target target = new Target();
        if (address == null || address.isEmpty() || address.equals("#"))
        {
            String temp = UUID.randomUUID().toString();
            receiver = _protonSession.receiver(temp);
            source.setDynamic(true);
        }
        else
        {
            receiver = _protonSession.receiver(address);
            source.setAddress(address);
        }
        receiver.setSource(source);
        receiver.setTarget(target);
        switch (mode)
        {
        case AT_MOST_ONCE:
            receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            receiver.setSenderSettleMode(SenderSettleMode.SETTLED);
            break;
        case AT_LEAST_ONCE:
            receiver.setReceiverSettleMode(ReceiverSettleMode.FIRST);
            receiver.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            break;
        case EXACTLY_ONCE:
            receiver.setReceiverSettleMode(ReceiverSettleMode.SECOND);
            receiver.setSenderSettleMode(SenderSettleMode.UNSETTLED);
            break;
        }
        receiver.open();

        ErisReceiverImpl receiverImpl = new ErisReceiverImpl(address, this, receiver, creditMode);
        receiverImpl.setDynamicAddress(source.getDynamic());
        _receivers.put(receiver, receiverImpl);
        receiver.setContext(receiverImpl);
        _conn.write();
        return receiverImpl;
    }

    @Override
    public void accept(ErisMessage msg, int... flags) throws org.eris.messaging.SessionException
    {
        setDispositionAndSettleIfRequired(convertMessage(msg), ACCEPTED);
    }

    @Override
    public void reject(ErisMessage msg, int... flags) throws org.eris.messaging.SessionException
    {
        setDispositionAndSettleIfRequired(convertMessage(msg), REJECTED);
    }

    @Override
    public void release(ErisMessage msg, int... flags) throws org.eris.messaging.SessionException
    {
        setDispositionAndSettleIfRequired(convertMessage(msg), RELEASED);
    }

    @Override
    public void close() throws org.eris.messaging.TransportException
    {
        _conn.closeSession(_protonSession);
    }

    @Override
    public void setCompletionListener(CompletionListener l) throws org.eris.messaging.SessionException
    {
        _completionListener = l;
    }

    IncomingErisMessage convertMessage(ErisMessage msg) throws org.eris.messaging.SessionException
    {
        if (!(msg instanceof IncomingErisMessage))
        {
            throw new org.eris.messaging.SessionException("The supplied message is not a valid type");
        }

        IncomingErisMessage m = (IncomingErisMessage) msg;

        if (m.getSessionID() != _id)
        {
            throw new org.eris.messaging.SessionException("The supplied message is not associated with this session");
        }

        return m;
    }

    void setDispositionAndSettleIfRequired(IncomingErisMessage msg, DeliveryState state)
    {
        Delivery d = _unsettled.get(msg.getSequence());
        d.disposition(state);
        if (d.getLink().getReceiverSettleMode() == ReceiverSettleMode.FIRST)
        {
            d.settle();
            ((ErisReceiverImpl) d.getLink().getContext()).decrementUnsettledCount();
        }
    }

    long getNextDeliveryTag()
    {
        return _deliveryTag.incrementAndGet();
    }

    long getNextIncomingSequence()
    {
        return _incomingSequence.incrementAndGet();
    }

    ErisConnectionImpl getConnection()
    {
        return _conn;
    }

    void closeLink(Link link) throws org.eris.messaging.TransportException
    {
        link.close();
        _conn.write();
    }

    void linkClosed(Link link)
    {
        if (link instanceof Sender)
        {
            _senders.remove(link);
        }
    }

    void write() throws org.eris.messaging.TransportException
    {
        _conn.write();
    }

    void checkPreConditions() throws org.eris.messaging.SessionException
    {
        if (_protonSession.getLocalState() != EndpointState.ACTIVE)
        {
            throw new org.eris.messaging.SessionException("ErisSession is closed");
        }
    }

    String getID()
    {
        return _id;
    }

    void addUnsettled(long id, Delivery d)
    {
        _unsettled.put(id, d);
    }

    CompletionListener getCompletionListener()
    {
        return _completionListener;
    }
}