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

import org.apache.qpid.proton.message.Message;

public class IncomingErisMessage extends ErisMessageImpl
{
    private String _sessionID;
    private String _deliveryTag;
    private long _sequence;
    
    IncomingErisMessage(String ssnID, String deliveryTag, long sequence, Message msg)
    {
        super(msg);
        _sessionID = ssnID;
        _deliveryTag = deliveryTag;
        _sequence = sequence;
    }

    String getSessionID()
    {
        return _sessionID;
    }

    String getDeliveryTag()
    {
        return _deliveryTag;
    }

    long getSequence()
    {
        return _sequence;
    }
}