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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.qpid.proton.Proton;
import org.apache.qpid.proton.amqp.transport.ReceiverSettleMode;
import org.apache.qpid.proton.amqp.transport.SenderSettleMode;
import org.apache.qpid.proton.engine.Connection;
import org.apache.qpid.proton.engine.Delivery;
import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Link;
import org.apache.qpid.proton.engine.Receiver;
import org.apache.qpid.proton.engine.Sasl;
import org.apache.qpid.proton.engine.Sender;
import org.apache.qpid.proton.engine.Session;
import org.apache.qpid.proton.engine.Transport;
import org.apache.qpid.proton.message.Message;
import org.eris.logging.Logger;
import org.eris.messaging.ErisConnection;
import org.eris.messaging.ErisSession;
import org.eris.messaging.ReceiverMode;
import org.eris.messaging.SenderMode;
import org.eris.messaging.Tracker;
import org.eris.threading.Threading;
import org.eris.transport.TransportException;

public class ErisConnectionImpl implements org.eris.transport.Receiver<ByteBuffer>, ErisConnection
{
    private static final Logger _logger = Logger.get(ErisConnectionImpl.class);

    enum State
    {
        UNINITIALIZED, ACTIVE, DETACHED, CLOSED
    };

    private org.eris.messaging.ConnectionSettings _settings;

    private org.eris.transport.NetworkConnection<ByteBuffer> _networkConnection;

    private org.eris.transport.Sender<ByteBuffer> _sender;

    private Transport _transport = Proton.transport();

    private Connection _connection;

    private State _state = State.UNINITIALIZED;

    private final Map<Session, ErisSessionImpl> _sessionMap = new ConcurrentHashMap<Session, ErisSessionImpl>();

    private final LinkedBlockingQueue<TrackerImpl> _notificationQueue = new LinkedBlockingQueue<TrackerImpl>();

    private final Object _lock = new Object();

    private Thread _notificationThread = null;

    public ErisConnectionImpl(String url)
    {
        this(new ConnectionSettingsImpl(url));        
    }

    public ErisConnectionImpl(String host, int port)
    {
        this(new ConnectionSettingsImpl(host, port));
    }

    public ErisConnectionImpl(org.eris.messaging.ConnectionSettings settings)
    {
        _settings = settings;
        setupNotifications();
    }

    @Override
    public void connect() throws org.eris.messaging.TransportException, org.eris.messaging.ConnectionException,
    org.eris.messaging.TimeoutException
    {
        _connection = Proton.connection();
        _connection.setContainer(UUID.randomUUID().toString());
        _connection.setHostname(_settings.getHost());
        _transport.bind(_connection);
        //doSasl(_transport.sasl());
        _connection.open();

        try
        {
            // hard code for now
            _networkConnection = new org.eris.transport.io.IoNetworkConnection(_settings);
            _networkConnection.setReceiver(this);
            _networkConnection.start();
        }
        catch (org.eris.transport.TransportException e)
        {
            throw new org.eris.messaging.TransportException("Exception occurred while making tcp connection to peer", e);
        }
        _sender = _networkConnection.getSender();

        write();
        try
        {
            synchronized (_lock)
            {
                if (_state == State.UNINITIALIZED)
                {
                    _lock.wait(getDefaultTimeout());
                }
            }
        }
        catch (InterruptedException e)
        {
        }
        if (_state == State.UNINITIALIZED)
        {
            throw new org.eris.messaging.TimeoutException("Timeout while waiting for connection to be ready");
        }
    }

    @Override
    public ErisSession createSession() throws org.eris.messaging.TransportException,
    org.eris.messaging.ConnectionException, org.eris.messaging.TimeoutException
    {
        synchronized (_lock)
        {
            if (_state == State.DETACHED)
            {
                //wait on failover
            }
            else if (_state != State.ACTIVE)
            {
                throw new org.eris.messaging.ConnectionException("ErisConnection is closed");
            }
            Session ssn = _connection.session();
            ErisSessionImpl session = new ErisSessionImpl(this, ssn);
            _sessionMap.put(ssn, session);
            ssn.open();
            write();
            return session;
        }
    }

    @Override
    public void close() throws org.eris.messaging.TransportException,
    org.eris.messaging.ConnectionException, org.eris.messaging.TimeoutException
    {
        synchronized (_lock)
        {
            _state = State.CLOSED;
        }

        _notificationThread.interrupt();
        try
        {
            try
            {
                _notificationThread.join(getDefaultTimeout());
            }
            catch (InterruptedException e)
            {
                throw new org.eris.messaging.ConnectionException("Interrupted while waiting for notification thead to complete");
            }
            if (_notificationThread.isAlive())
            {
                throw new org.eris.messaging.TimeoutException("Time out while waiting for notification thread to complete");
            }
        }
        finally
        {
            _connection.close();
            write();
            //Should we wait until the remote end close the connection?
            try
            {
                _networkConnection.close();
            }
            catch (TransportException e)
            {
                throw new org.eris.messaging.TransportException("Error closing network connection",e);
            }
        }
    }

    // Needs to expand to handle other mechs
    void doSasl(Sasl sasl)
    {
        if (sasl != null)
        {
            sasl.client();
            sasl.setMechanisms(new String[] { "ANONYMOUS" });
        }
    }

    void write() throws org.eris.messaging.TransportException
    {
        try
        {
            while (_transport.pending() > 0)
            {
                ByteBuffer data = _transport.getOutputBuffer();
                _sender.send(data);
                _sender.flush();
                _transport.outputConsumed();
            }
        }
        catch (org.eris.transport.TransportException e)
        {
            _logger.error(e, "Error while writing to ouput stream");
            throw new org.eris.messaging.TransportException("Error while writing to ouput stream", e);
        }
    }

    @Override
    public void received(ByteBuffer data)
    {
        while (data.hasRemaining())
        {
            ByteBuffer buf = _transport.getInputBuffer();
            int maxAllowed = Math.min(data.remaining(), buf.remaining());
            ByteBuffer temp = data.duplicate();
            temp.limit(data.position() + maxAllowed);
            buf.put(temp);
            _transport.processInput();
            data.position(data.position() + maxAllowed);
        }

        if (_state == State.UNINITIALIZED)
        {
            if (_connection.getRemoteState() == EndpointState.ACTIVE)
            {
                synchronized (_lock)
                {
                    _state = State.ACTIVE;
                    _lock.notifyAll();
                }
            }
        }
        else
        {
            processSessions();
            processLinks();
            processDeliveries();
        }
    }

    @Override
    public void exception(Throwable t)
    {

    }

    @Override
    public void closed()
    {
        // TODO Auto-generated method stub
    }

    public void processDeliveries()
    {
        Delivery delivery = _connection.getWorkHead();
        while (delivery != null)
        {
            if (delivery.isReadable() && !delivery.isPartial())
            {
                incomming(delivery);
            }
            if (delivery.isUpdated())
            {
                processUpdate(delivery);
            }
            Delivery next = delivery.getWorkNext();
            delivery.clear();
            delivery = next;
        }
    }

    void incomming(Delivery delivery)
    {
        Receiver receiver = (Receiver)delivery.getLink();
        int size = delivery.pending();
        byte[] buffer = new byte[size];
        int read = receiver.recv( buffer, 0, buffer.length );
        if (read != size) {
            // TODO need to handle this error
        }
        Message msg = Proton.message();
        msg.decode(buffer, 0, read);
        ErisReceiverImpl recv = (ErisReceiverImpl)receiver.getContext();
        String tag = String.valueOf(delivery.getTag());
        long sequence = recv.getSession().getNextIncomingSequence();
        recv.getSession().addUnsettled(sequence, delivery);
        recv.enqueue(new IncomingErisMessage(recv.getSession().getID(), tag, sequence, msg));
    }

    void processUpdate(Delivery delivery)
    {
        Link link = delivery.getLink(); 
        if (link instanceof Sender)
        {
            if (delivery.getRemoteState() != null)
            {
                delivery.disposition(delivery.getRemoteState());
                TrackerImpl tracker = (TrackerImpl) delivery.getContext();
                tracker.update(delivery.getRemoteState());
            }
            if (delivery.remotelySettled() && link.getSenderSettleMode() == SenderSettleMode.UNSETTLED)
            {
                TrackerImpl tracker = (TrackerImpl) delivery.getContext();
                delivery.settle();
                tracker.markSettled();
                if (tracker.getSession().getCompletionListener() != null)
                {
                    _notificationQueue.add(tracker);
                }
            }
        }
        else
        {
            if (delivery.remotelySettled() && link.getReceiverSettleMode() == ReceiverSettleMode.SECOND)
            {
                delivery.settle();
                ((ErisReceiverImpl)link.getContext()).decrementUnsettledCount();
            }
        }
    }

    void processSessions()
    {
        Session ssn = _connection.sessionHead(EndpointStateHelper.ANY, EndpointStateHelper.CLOSED);
        while (ssn != null)
        {
            ssn.close();
            _sessionMap.remove(ssn);
            ssn = ssn.next(EndpointStateHelper.ANY, EndpointStateHelper.CLOSED);
        }
    }

    void processLinks()
    {
        Link link = _connection.linkHead(EndpointStateHelper.ACTIVE, EndpointStateHelper.ACTIVE);
        while (link != null)
        {
            if (link instanceof Sender)
            {
                ErisSenderImpl sender = (ErisSenderImpl)link.getContext();
                if (sender.isDynamicAddress())
                {
                    sender.setAddress(link.getRemoteTarget().getAddress());
                }
            }
            else
            {
                ErisReceiverImpl receiver = (ErisReceiverImpl)link.getContext();
                if (receiver.isDynamicAddress())
                {
                    receiver.setAddress(link.getRemoteSource().getAddress());
                }
            }
            link = link.next(EndpointStateHelper.ACTIVE, EndpointStateHelper.ACTIVE);
        }

        link = _connection.linkHead(EndpointStateHelper.ANY, EndpointStateHelper.CLOSED);
        while (link != null)
        {
            if (link.getRemoteCondition() != null)
               _logger.error(link.getRemoteCondition().toString());
            link.close();
            _sessionMap.get(link.getSession()).linkClosed(link);
            link = link.next(EndpointStateHelper.ANY, EndpointStateHelper.CLOSED);
        }
    }

    void closeSession(Session ssn) throws org.eris.messaging.TransportException
    {
        ssn.close();
        write();
    }

    long getDefaultTimeout()
    {
        return _settings.getConnectTimeout();
    }

    void setupNotifications()
    {
        try
        {
            _notificationThread = Threading.getThreadFactory().createThread(
                    new Runnable () {

                        @Override
                        public void run()
                        {
                            while(_state != State.CLOSED)
                            {
                                try
                                {
                                    TrackerImpl t = _notificationQueue.take();
                                    if (t.getSession().getCompletionListener() != null)
                                    {
                                        t.getSession().getCompletionListener().completed(t);
                                    }
                                }
                                catch (InterruptedException e)
                                {
                                    //ignore
                                }
                                catch (NullPointerException e)
                                {
                                    // There is always a chance of completion listener being set to null
                                    // btw the check and the time it's being called.
                                }
                            }
                        }

                    });
            //_notificationThread.setName(name);
            _notificationThread.start();
        }
        catch (Exception e)
        {
            throw new RuntimeException("Error creating Notification thread",e);
        }
    }

    public static void main(String[] args) throws Exception
    {
        ErisConnectionImpl con = new ErisConnectionImpl("localhost", 5672);
        con.connect();
        ErisSessionImpl ssn = (ErisSessionImpl) con.createSession();
        ssn.setCompletionListener(new org.eris.messaging.CompletionListener(){

            @Override
            public void completed(Tracker t)
            {
                _logger.info("Got notified of message completion");

            }});

        /*ErisSenderImpl sender = (ErisSenderImpl) ssn.createSender("#", SenderMode.AT_LEAST_ONCE);
        ErisMessageImpl msg = new ErisMessageImpl();
        msg.setContent("Hello World");
        Tracker t = sender.send(msg);
        t.awaitSettlement();

        ErisReceiverImpl receiver = (ErisReceiverImpl) ssn.createReceiver("#",ReceiverMode.AT_LEAST_ONCE);
        msg = (ErisMessageImpl)receiver.receive();
        ssn.accept(msg);
        System.out.println("Msg : " + msg.getContent());
        con.close();*/

        ErisReceiverImpl receiver = (ErisReceiverImpl) ssn.createReceiver("#",ReceiverMode.AT_LEAST_ONCE);
        Thread.sleep(1000); // Giving time for the peer to send the address created at the server side.
        // I need to find a way to coordinate this.
        String tempAddress = receiver.getAddress();
        ErisSenderImpl sender = (ErisSenderImpl) ssn.createSender(tempAddress, SenderMode.AT_LEAST_ONCE);
        ErisMessageImpl msg = new ErisMessageImpl();
        msg.setContent("Hello World");
        Tracker t = sender.send(msg);
        t.awaitSettlement();

        msg = (ErisMessageImpl)receiver.receive();
        ssn.accept(msg);
        _logger.info("Msg : " + msg.getContent());
        con.close();
    }
}