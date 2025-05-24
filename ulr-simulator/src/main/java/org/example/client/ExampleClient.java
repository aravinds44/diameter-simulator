package org.example.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jdiameter.api.Answer;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpDataException;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.Configuration;
import org.jdiameter.api.EventListener;
import org.jdiameter.api.IllegalDiameterStateException;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Message;
import org.jdiameter.api.MetaData;
import org.jdiameter.api.Network;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.Request;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.Session;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.Stack;
import org.jdiameter.api.StackType;
import org.jdiameter.server.impl.StackImpl;
import org.jdiameter.server.impl.helpers.XMLConfiguration;
import org.mobicents.diameter.dictionary.AvpDictionary;
import org.mobicents.diameter.dictionary.AvpRepresentation;

public class ExampleClient implements EventListener<Request, Answer> {

  private static final Logger log = LoggerFactory.getLogger(ExampleClient.class);

  private static final String configFile = "org/example/client/client-jdiameter-config.xml";
  private static final String dictionaryFile = "org/example/client/dictionary.xml";

  // our destination
  private static final String serverHost = "127.0.0.1";
  private static final String serverPort = "3868";
  private static final String serverURI = "aaa://" + serverHost + ":" + serverPort;

  // our realm
  private static final String realmName = "exchange.example.org";

  // 3GPP S6a/S6d interface parameters
  // ULR = Update Location Request
  private static final int commandCode = 316; // Update-Location
  private static final long vendorID = 10415; // 3GPP vendor ID
  private static final long applicationID = 16777251; // 3GPP S6a/S6d
  private ApplicationId authAppId = ApplicationId.createByAuthAppId(applicationID);

  // ULR specific AVPs
  private static final int RAT_TYPE = 1032; // Radio Access Type
  private static final int ULR_FLAGS = 1405; // ULR-Flags
  private static final int VISITED_PLMN_ID = 1407; // Visited-PLMN-Id
  private static final int USER_NAME = 1; // User-Name (IMSI)

  // Sample IMSI - International Mobile Subscriber Identity
  private static final String SUBSCRIBER_IMSI = "123456789012345";

  // Sample PLMN ID (3 bytes in hex format)
  private static final byte[] SAMPLE_PLMN_ID = new byte[]{0x00, 0x01, 0x02};

  // Dictionary, for informational purposes.
  private AvpDictionary dictionary = AvpDictionary.INSTANCE;

  // stack and session factory
  private Stack stack;
  private SessionFactory factory;

  // ////////////////////////////////////////
  // Objects which will be used in action //
  // ////////////////////////////////////////
  private Session session; // session used as handle for communication
  private boolean finished = false; // boolean telling if we finished our interaction

  public void initStack() {
    if (log.isInfoEnabled()) {
      log.info("Initializing Client Stack...");
    }
    InputStream is = null;
    try {
      // Parse dictionary, it is used for user friendly info.
      dictionary.parseDictionary(this.getClass().getClassLoader().getResourceAsStream(dictionaryFile));
      log.info("AVP Dictionary successfully parsed.");

      this.stack = new StackImpl();
      // Parse stack configuration
      is = this.getClass().getClassLoader().getResourceAsStream(configFile);
      Configuration config = new XMLConfiguration(is);
      factory = stack.init(config);
      if (log.isInfoEnabled()) {
        log.info("Stack Configuration successfully loaded.");
      }
      // Print info about applicatio
      Set<org.jdiameter.api.ApplicationId> appIds = stack.getMetaData().getLocalPeer().getCommonApplications();

      log.info("Diameter Stack  :: Supporting " + appIds.size() + " applications.");
      for (org.jdiameter.api.ApplicationId x : appIds) {
        log.info("Diameter Stack  :: Common :: " + x);
      }
      is.close();
      // Register network req listener, even though we wont receive requests
      // this has to be done to inform stack that we support application
      Network network = stack.unwrap(Network.class);
      network.addNetworkReqListener(new NetworkReqListener() {

        @Override
        public Answer processRequest(Request request) {
          // this wontbe called.
          return null;
        }
      }, this.authAppId); // passing our S6a app id.

    } catch (Exception e) {
      e.printStackTrace();
      if (this.stack != null) {
        this.stack.destroy();
      }

      if (is != null) {
        try {
          is.close();
        } catch (IOException e1) {
          // TODO Auto-generated catch block
          e1.printStackTrace();
        }
      }
      return;
    }

    MetaData metaData = stack.getMetaData();
    // ignore for now.
    if (metaData.getStackType() != StackType.TYPE_SERVER || metaData.getMinorVersion() <= 0) {
      stack.destroy();
      if (log.isErrorEnabled()) {
        log.error("Incorrect driver");
      }
      return;
    }

    try {
      if (log.isInfoEnabled()) {
        log.info("Starting stack");
      }
      stack.start();
      if (log.isInfoEnabled()) {
        log.info("Stack is running.");
      }
    } catch (Exception e) {
      e.printStackTrace();
      stack.destroy();
      return;
    }
    if (log.isInfoEnabled()) {
      log.info("Stack initialization successfully completed.");
    }
  }

  /**
   * @return
   */
  private boolean finished() {
    return this.finished;
  }

  /**
   * Start client and send ULR message
   */
  private void start() {
    try {
      // wait for connection to peer
      try {
        Thread.currentThread().sleep(5000);
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
      // do send
      this.session = this.factory.getNewSession("BadCustomSessionId;YesWeCanPassId;" + System.currentTimeMillis());
      sendULR();
    } catch (InternalException e) {
      e.printStackTrace();
    } catch (IllegalDiameterStateException e) {
      e.printStackTrace();
    } catch (RouteException e) {
      e.printStackTrace();
    } catch (OverloadException e) {
      e.printStackTrace();
    }
  }

  private void sendULR() throws InternalException, IllegalDiameterStateException, RouteException, OverloadException {
    Request ulr = this.session.createRequest(commandCode, this.authAppId, realmName, serverHost);

    AvpSet requestAvps = ulr.getAvps();

    // Add mandatory AVPs for ULR

    // User-Name (IMSI)
    requestAvps.addAvp(USER_NAME, SUBSCRIBER_IMSI, false);

    // Visited-PLMN-Id
    requestAvps.addAvp(VISITED_PLMN_ID, SAMPLE_PLMN_ID, vendorID, false, false);

    // ULR-Flags - 1 means single-registration-indication
    requestAvps.addAvp(ULR_FLAGS, 1, vendorID, false, false, true);

    // RAT-Type - 1004 = EUTRAN (4G)
    requestAvps.addAvp(RAT_TYPE, 1004, vendorID, false, false, true);

    // Send the request
    log.info("Sending Update-Location-Request");
    this.session.send(ulr, this);
    dumpMessage(ulr, true); // dump info on console
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdiameter.api.EventListener#receivedSuccessMessage(org.jdiameter
   * .api.Message, org.jdiameter.api.Message)
   */
  public void handleSuccessAnswer(Request request, Answer answer, Session session) {
    dumpMessage(answer, false);

    if (answer.getCommandCode() != commandCode) {
      log.error("Received bad answer: " + answer.getCommandCode());
      return;
    }

    AvpSet answerAvpSet = answer.getAvps();
    Avp resultCodeAvp = answer.getResultCode();

    try {
      long resultCode = resultCodeAvp.getUnsigned32();
      log.info("Result code: " + resultCode);

      if (resultCode == 2001) { // DIAMETER_SUCCESS
        log.info("Successfully received ULA (Update-Location-Answer)");

        // Check for any subscription data or other important AVPs
        // Here you can handle specific 3GPP AVPs from the ULA

        // For example, check for Subscription-Data AVP
        Avp subDataAvp = answerAvpSet.getAvp(1400, vendorID); // 1400 is Subscription-Data AVP
        if (subDataAvp != null) {
          log.info("Subscription data is available in the answer");
          // Process subscription data here
        }
      } else {
        log.error("ULA contained error: " + resultCode);
      }

      // Release the session
      session.release();
      session = null;

    } catch (AvpDataException e) {
      log.error("Failed to parse AVPs in Answer", e);
    }
  }

  @Override
  public void receivedSuccessMessage(Request request, Answer answer) {
    return;
  }

  /*
   * (non-Javadoc)
   *
   * @see org.jdiameter.api.EventListener#timeoutExpired(org.jdiameter.api.
   * Message)
   */
  @Override
  public void timeoutExpired(Request request) {
    log.error("Request timed out! " + request);
    finished = true;
  }

  public void dumpMessage(Message message, boolean sending) {
    if (log.isInfoEnabled()) {
      log.info((sending ? "Sending " : "Received ") + (message.isRequest() ? "Request: " : "Answer: ")
          + message.getCommandCode() + "\nE2E:" + message.getEndToEndIdentifier() + "\nHBH:"
          + message.getHopByHopIdentifier() + "\nAppID:" + message.getApplicationId());
      log.info("AVPS[" + message.getAvps().size() + "]: \n");
      try {
        printAvps(message.getAvps());
      } catch (AvpDataException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
      }
    }
  }

  private void printAvps(AvpSet avpSet) throws AvpDataException {
    printAvpsAux(avpSet, 0);
  }

  /**
   * Prints the AVPs present in an AvpSet with a specified 'tab' level
   *
   * @param avpSet the AvpSet containing the AVPs to be printed
   * @param level  an int representing the number of 'tabs' to make a pretty print
   * @throws AvpDataException
   */
  private void printAvpsAux(AvpSet avpSet, int level) throws AvpDataException {
    String prefix = "                      ".substring(0, level * 2);

    for (Avp avp : avpSet) {
      AvpRepresentation avpRep = AvpDictionary.INSTANCE.getAvp(avp.getCode(), avp.getVendorId());

      if (avpRep != null && avpRep.getType().equals("Grouped")) {
        log.info(prefix + "<avp name=\"" + avpRep.getName() + "\" code=\"" + avp.getCode() + "\" vendor=\""
            + avp.getVendorId() + "\">");
        printAvpsAux(avp.getGrouped(), level + 1);
        log.info(prefix + "</avp>");
      } else if (avpRep != null) {
        String value = "";

        if (avpRep.getType().equals("Integer32")) {
          value = String.valueOf(avp.getInteger32());
        } else if (avpRep.getType().equals("Integer64") || avpRep.getType().equals("Unsigned64")) {
          value = String.valueOf(avp.getInteger64());
        } else if (avpRep.getType().equals("Unsigned32")) {
          value = String.valueOf(avp.getUnsigned32());
        } else if (avpRep.getType().equals("Float32")) {
          value = String.valueOf(avp.getFloat32());
        } else {
          // value = avp.getOctetString();

          value = new String(avp.getOctetString(), StandardCharsets.UTF_8);
        }

        log.info(prefix + "<avp name=\"" + avpRep.getName() + "\" code=\"" + avp.getCode() + "\" vendor=\""
            + avp.getVendorId() + "\" value=\"" + value + "\" />");
      }
    }
  }

  public Stack getStack() {
    return stack;
  }

  public SessionFactory getSessionFactory() {
    return factory;
  }

  public int getCommandCode() {
    return commandCode;
  }

  public ApplicationId getAuthAppId() {
    return authAppId;
  }

  public String getRealmName() {
    return realmName;
  }

  public String getServerHost() {
    return serverHost;
  }

  public long getVendorID() {
    return vendorID;
  }

  public int getUSER_NAME() {
    return USER_NAME;
  }

  public int getVISITED_PLMN_ID() {
    return VISITED_PLMN_ID;
  }

  public int getULR_FLAGS() {
    return ULR_FLAGS;
  }

  public int getRAT_TYPE() {
    return RAT_TYPE;
  }

}

