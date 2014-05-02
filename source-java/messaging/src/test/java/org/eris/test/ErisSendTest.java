/*
 * Copyright 2005-2014 Red Hat, Inc.
 * Red Hat licenses this file to you under the Apache License, version
 * 2.0 (the "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied.  See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.eris.test;


import org.eris.Messaging;
import org.eris.logging.Logger;
import org.eris.messaging.ErisConnection;
import org.eris.messaging.ErisMessage;
import org.eris.messaging.ErisReceiver;
import org.eris.messaging.ReceiverMode;
import org.eris.messaging.ErisSender;
import org.eris.messaging.SenderMode;
import org.eris.messaging.ErisSession;
import org.eris.messaging.Tracker;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * @author Clebert Suconic
 * @author Justin Bertram
 */
public class ErisSendTest
{
   Logger logger = Logger.get(ErisSendTest.class);
//   static MockBroker server;

   @BeforeClass
   public static void setUp() throws Exception
   {
      /**
       * Start HornetQ separately with this acceptor:
       * <acceptor name="amqp-acceptor">
       *    <factory-class>org.hornetq.core.remoting.impl.netty.NettyAcceptorFactory</factory-class>
       *    <param key="protocols" value="AMQP"/>
       *    <param key="port" value="5672"/>
       * </acceptor>
       *
       * and this queue:
       * <queues>
       *    <queue name="mybox">
       *       <address>mybox</address>
       *    </queue>
       * </queues>
       *
       */

//      server = new MockBroker();
//
//      Thread serverThread = new Thread(new Runnable()
//      {
//         @Override
//         public void run()
//         {
//            try
//            {
//               while (true)
//               {
//                  server.doWait();
//                  server.acceptConnections();
//                  server.processConnections();
//               }
//            }
//            catch (Exception e)
//            {
//               e.printStackTrace();
//            }
//         }
//      });
//      serverThread.start();
   }

   @Test
   public void testSend() throws Exception
   {
      final String MESSAGE_CONTENT = "Hello World";

      logger.info("Creating erisConnection...");
      ErisConnection erisConnection = Messaging.connection("localhost", 5672);
      logger.info("Connecting erisConnection...");
      erisConnection.connect();

      logger.info("Creating erisSession...");
      ErisSession erisSession = erisConnection.createSession();

      logger.info("Creating message...");
      ErisMessage msg = Messaging.message();
      msg.setContent(MESSAGE_CONTENT);

      logger.info("Creating erisSender...");
      ErisSender erisSender = erisSession.createSender("mybox", SenderMode.AT_LEAST_ONCE);
      logger.info("Sending message " + msg.getContent() + "...");
      Tracker tracker = erisSender.send(msg);
      logger.info("Awaiting settlement...");
      tracker.awaitSettlement(5000);
      erisSender.close();

      msg = null;

      logger.info("Creating erisReceiver...");
      ErisReceiver erisReceiver = erisSession.createReceiver("mybox", ReceiverMode.AT_LEAST_ONCE);
      logger.info("Receiving message...");
      msg = erisReceiver.receive();
      logger.info("Accepting message...");
      erisSession.accept(msg);
      logger.info("Msg: " + msg.getContent());
      Assert.assertTrue(MESSAGE_CONTENT.equals(msg.getContent()));
      logger.info("Closing erisConnection...");
      erisReceiver.close();
      erisSession.close();
      erisConnection.close();
   }

   @AfterClass
   public static void tearDown()
   {

   }
}
