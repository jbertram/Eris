package org.eris.messaging.amqp.proton;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.qpid.proton.engine.EndpointState;
import org.apache.qpid.proton.engine.Receiver;
import org.eris.messaging.CreditMode;
import org.eris.messaging.ErisMessage;
import org.eris.messaging.ErisReceiver;

public class ErisReceiverImpl implements ErisReceiver
{
    private String _address;

    private ErisSessionImpl _ssn;

    private Receiver _protonReceiver;

    private int _capacity = Integer.getInteger("eris.receiver.capacity", 1);

    private CreditMode _creditMode;

    private LinkedBlockingQueue<ErisMessage> _queue;

    private AtomicInteger _unsettled = new AtomicInteger(0);

    private boolean _dynamic = false;

    ErisReceiverImpl(String address, ErisSessionImpl ssn, Receiver receiver, CreditMode creditMode)
            throws org.eris.messaging.TransportException
            {
        _address = address;
        _ssn = ssn;
        _protonReceiver = receiver;
        _queue = new LinkedBlockingQueue<ErisMessage>();
        _creditMode = creditMode;
        if (_creditMode == CreditMode.AUTO && _capacity > 0)
        {
            issueCredits(_capacity, false);
        }
        else
        {
            _capacity = 0;
        }
            }

    @Override
    public String getAddress()
    {
        return _address;
    }

    @Override
    public int getCapacity()
    {
        return _capacity;
    }

    @Override
    public int getAvailable()
    {
        return _queue.size();
    }

    @Override
    public int getUnsettled()
    {
        return _unsettled.get();
    }

    @Override
    public CreditMode getCreditMode()
    {
        return _creditMode;
    }

    @Override
    public ErisMessage receive() throws org.eris.messaging.TransportException,
    org.eris.messaging.ReceiverException
    {
        try
        {
            return receive(0);
        }
        catch (org.eris.messaging.TimeoutException e)
        {
            // Only to get it compiling. This exception will never be thrown
            // when the timeout == 0.
            return null;
        }
    }

    @Override
    public ErisMessage receive(long timeout) throws org.eris.messaging.TransportException,
    org.eris.messaging.ReceiverException, org.eris.messaging.TimeoutException
    {
        checkClosed();
        if (_creditMode == CreditMode.EXPLICT && _capacity == 0)
        {
            throw new org.eris.messaging.ReceiverException(
                    "Since CreditMode is EXPLICIT, you need to explicity set the capacity before calling receive");
        }
        issuePreReceiveCredit();
        ErisMessage msg = null;
        try
        {
            if (timeout == 0)
            {
                msg = _queue.take();
            }
            else
            {
                msg = _queue.poll(timeout, TimeUnit.MILLISECONDS);
            }
        }
        catch (InterruptedException e)
        {
            checkClosed();
        }

        if (msg != null)
        {
            issuePostReceiveCredit();
            return msg;
        }
        else
        {
            checkClosed();
            if (timeout == 0)
            {
                throw new org.eris.messaging.ReceiverException("Receive operation was interrupted");
            }
            else
            {
                throw new org.eris.messaging.TimeoutException("Timeout waiting for message to be available");
            }
        }
    }

    @Override
    public void setCapacity(int credits) throws org.eris.messaging.TransportException,
    org.eris.messaging.ReceiverException
    {
        checkClosed();
        if (credits < 0)
        {
            throw new org.eris.messaging.ReceiverException("Capacity cannot be negative");
        }
        else if (_creditMode == CreditMode.EXPLICT && credits == 0)
        {
            throw new org.eris.messaging.ReceiverException("When CreditMode is EXPLICIT, capacity cannot be zero");
        }
        _capacity = credits;
        cancelPrevCredits();
        if (_capacity > 0)
        {
            issueCredits(credits, false);
        }
    }

    @Override
    public void close() throws org.eris.messaging.TransportException
    {
        _ssn.closeLink(_protonReceiver);
    }

    void checkClosed() throws org.eris.messaging.ReceiverException
    {
        if (_protonReceiver.getLocalState() != EndpointState.ACTIVE)
        {
            throw new org.eris.messaging.ReceiverException("ErisReceiver is closed");
        }
    }

    void enqueue(ErisMessageImpl msg)
    {
        try
        {
            _queue.put(msg);
            _unsettled.incrementAndGet();
        }
        catch (InterruptedException e)
        {
            // ignore?
        }
    }

    void decrementUnsettledCount()
    {
        _unsettled.decrementAndGet();
    }

    void issuePreReceiveCredit() throws org.eris.messaging.TransportException
    {
        if (_creditMode == CreditMode.AUTO && _capacity == 0 && _queue.isEmpty())
        {
            issueCredits(1, true);
        }
    }

    void issuePostReceiveCredit() throws org.eris.messaging.TransportException
    {
        if (_creditMode == CreditMode.AUTO)
        {
            if (_capacity == 1)
            {
                issueCredits(1, false);
            }
            else if (_unsettled.get() < _capacity / 2)
            {
                issueCredits(_capacity - _unsettled.get(), false);
            }
        }
    }

    void cancelPrevCredits() throws org.eris.messaging.TransportException
    {
        _protonReceiver.flow(0);
        _ssn.write();
    }

    void issueCredits(int credits, boolean drain) throws org.eris.messaging.TransportException
    {
        if (drain)
        {
            _protonReceiver.setDrain(true);
        }
        _protonReceiver.flow(credits);
        _ssn.write();
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

    ErisSessionImpl getSession()
    {
        return _ssn;
    }
}