package org.eris.messaging.amqp.proton;

import org.apache.qpid.proton.amqp.messaging.Accepted;
import org.apache.qpid.proton.amqp.messaging.Rejected;
import org.apache.qpid.proton.amqp.messaging.Released;
import org.apache.qpid.proton.amqp.transport.DeliveryState;
import org.eris.messaging.Tracker;
import org.eris.messaging.TrackerState;
import org.eris.util.ConditionManager;
import org.eris.util.ConditionManagerTimeoutException;

public class TrackerImpl implements Tracker
{
    private final ErisSessionImpl _ssn;
    private TrackerState _state = null;
    private ConditionManager _pending = new ConditionManager(true);
    private boolean _settled = false;

    TrackerImpl(ErisSessionImpl ssn)
    {
        _ssn = ssn;
        _state = TrackerState.UNKNOWN;
    }

    void setState(TrackerState state)
    {
        _state = state;
    }

    void markSettled()
    {
        _settled = true;
        _pending.setValueAndNotify(false);
    }

    void update(DeliveryState state)
    {
        if (state instanceof Accepted)
        {
            setState(TrackerState.ACCEPTED);
        }
        else if (state instanceof Rejected)
        {
            setState(TrackerState.REJECTED);
        }
        else if (state instanceof Released)
        {
            setState(TrackerState.RELEASED);
        }
    }

    boolean isTerminalState()
    {
        switch (_state)
        {
        case ACCEPTED:
        case REJECTED:
        case RELEASED:
            return true;
        default:
            return false;
        }
    }

    ErisSessionImpl getSession()
    {
        return _ssn;
    }

    @Override
    public TrackerState getState()
    {
        return _state;
    }

   @Override
   public void awaitSettlement()
   {
      _pending.waitUntilFalse();
   }

   @Override
   public void awaitSettlement(long timeout) throws ConditionManagerTimeoutException
   {
      _pending.waitUntilFalse(timeout);
   }

    @Override
    public boolean isSettled()
    {
        return _settled;
    }
}
