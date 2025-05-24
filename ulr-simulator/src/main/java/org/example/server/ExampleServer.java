package org.example.server;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
//import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

//import org.apache.log4j.Logger;
//import org.apache.log4j.PropertyConfigurator;
import org.jdiameter.api.Answer;
import org.jdiameter.api.ApplicationId;
import org.jdiameter.api.Avp;
import org.jdiameter.api.AvpDataException;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.Configuration;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.Message;
import org.jdiameter.api.MetaData;
import org.jdiameter.api.Network;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.Request;
import org.jdiameter.api.Session;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.Stack;
import org.jdiameter.api.StackType;
import org.jdiameter.server.impl.StackImpl;
import org.jdiameter.server.impl.helpers.XMLConfiguration;
import org.mobicents.diameter.dictionary.AvpDictionary;
import org.mobicents.diameter.dictionary.AvpRepresentation;

/**
 * @author baranowb
 */
public class ExampleServer implements NetworkReqListener {
  private static final Logger log = LoggerFactory.getLogger(ExampleServer.class);

  private static final String configFile = "org/example/server/server-jdiameter-config.xml";
  private static final String dictionaryFile = "org/example/client/dictionary.xml";
  private static final String realmName = "exchange.example.org";

  // 3GPP S6a/S6d interface parameters
  // ULR = Update Location Request
  private static final int commandCode = 316;  // Update-Location
  private static final long vendorID = 10415;  // 3GPP vendor ID
  private static final long applicationID = 16777251;  // 3GPP S6a/S6d
  private ApplicationId authAppId = ApplicationId.createByAuthAppId(applicationID);

  // ULR specific AVPs
  private static final int RAT_TYPE = 1032;  // Radio Access Type
  private static final int ULR_FLAGS = 1405;  // ULR-Flags
  private static final int VISITED_PLMN_ID = 1407;  // Visited-PLMN-Id
  private static final int USER_NAME = 1;    // User-Name (IMSI)

  // ULA specific AVPs
  private static final int ULA_FLAGS = 1406;  // ULA-Flags
  private static final int SUBSCRIPTION_DATA = 1400;  // Subscription-Data (grouped)
  private static final int MSISDN = 701;      // MSISDN

  // Result codes
  private static final int DIAMETER_SUCCESS = 2001;
  private static final int DIAMETER_ERROR_USER_UNKNOWN = 5001;
  private static final int DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION = 5420;

  private AvpDictionary dictionary = AvpDictionary.INSTANCE;
  private Stack stack;
  private SessionFactory factory;

  // ////////////////////////////////////////
  // Objects which will be used in action //
  // ////////////////////////////////////////
  private Session session;
  private boolean finished = false;

  public void initStack() {
    if (log.isInfoEnabled()) {
      log.info("Initializing Server Stack...");
    }
    InputStream is = null;
    try {
      dictionary.parseDictionary(this.getClass().getClassLoader().getResourceAsStream(dictionaryFile));
      log.info("AVP Dictionary successfully parsed.");
      this.stack = new StackImpl();

      is = this.getClass().getClassLoader().getResourceAsStream(configFile);

      Configuration config = new XMLConfiguration(is);
      factory = stack.init(config);
      if (log.isInfoEnabled()) {
        log.info("Stack Configuration successfully loaded.");
      }

      Set<org.jdiameter.api.ApplicationId> appIds = stack.getMetaData().getLocalPeer().getCommonApplications();

      log.info("Diameter Stack  :: Supporting " + appIds.size() + " applications.");
      for (org.jdiameter.api.ApplicationId x : appIds) {
        log.info("Diameter Stack  :: Common :: " + x);
      }
      is.close();
      Network network = stack.unwrap(Network.class);
      network.addNetworkReqListener(this, this.authAppId);
    } catch (Exception e) {
      e.printStackTrace();
      if (this.stack != null) {
        this.stack.destroy();
      }

      if (is != null) {
        try {
          is.close();
        } catch (IOException e1) {
          e1.printStackTrace();
        }
      }
      return;
    }

    MetaData metaData = stack.getMetaData();
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

  private void dumpMessage(Message message, boolean sending) {
    if (log.isInfoEnabled()) {
      log.info((sending ? "Sending " : "Received ") + (message.isRequest() ? "Request: " : "Answer: ") + message.getCommandCode() + "\nE2E:"
          + message.getEndToEndIdentifier() + "\nHBH:" + message.getHopByHopIdentifier() + "\nAppID:" + message.getApplicationId());
      log.info("AVPS[" + message.getAvps().size() + "]: \n");
      try {
        printAvps(message.getAvps());
      } catch (AvpDataException e) {
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
   * @param level  an int representing the number of 'tabs' to make a pretty
   *               print
   * @throws AvpDataException
   */
  private void printAvpsAux(AvpSet avpSet, int level) throws AvpDataException {
    String prefix = "                      ".substring(0, level * 2);

    for (Avp avp : avpSet) {
      AvpRepresentation avpRep = AvpDictionary.INSTANCE.getAvp(avp.getCode(), avp.getVendorId());

      if (avpRep != null && avpRep.getType().equals("Grouped")) {
        log.info(prefix + "<avp name=\"" + avpRep.getName() + "\" code=\"" + avp.getCode() + "\" vendor=\"" + avp.getVendorId() + "\">");
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
          value = new String(avp.getOctetString(), StandardCharsets.UTF_8);
        }

        log.info(prefix + "<avp name=\"" + avpRep.getName() + "\" code=\"" + avp.getCode() + "\" vendor=\"" + avp.getVendorId()
            + "\" value=\"" + value + "\" />");
      }
    }
  }

  /**
   * @return
   */
  private boolean finished() {
    return this.finished;
  }


  /*
   * (non-Javadoc)
   *
   * @see
   * org.jdiameter.api.NetworkReqListener#processRequest(org.jdiameter.api
   * .Request)
   */
  @Override
  public Answer processRequest(Request request) {
    dumpMessage(request, false);

    if (request.getCommandCode() != commandCode) {
      log.error("Received unsupported command: " + request.getCommandCode());
      return null;
    }

    AvpSet requestAvpSet = request.getAvps();

    // Create new session
    try {
      this.session = this.factory.getNewSession(request.getSessionId());
    } catch (InternalException e) {
      log.error("Failed to create session", e);
      return null;
    }

    // Check if this is ULR
    log.info("Received Update-Location-Request (ULR)");

    // Validate mandatory AVPs
    Avp userNameAvp = requestAvpSet.getAvp(USER_NAME);
    Avp visitedPlmnIdAvp = requestAvpSet.getAvp(VISITED_PLMN_ID, vendorID);

    if (userNameAvp == null) {
      log.error("Request missing User-Name (IMSI) AVP");
      return createULA(request, 5004); // DIAMETER_MISSING_AVP
    }

    if (visitedPlmnIdAvp == null) {
      log.error("Request missing Visited-PLMN-ID AVP");
      return createULA(request, 5004); // DIAMETER_MISSING_AVP
    }

    // Extract and validate the IMSI
    String imsi;
    try {
      imsi = userNameAvp.getUTF8String();
      log.info("Processing ULR for IMSI: " + imsi);

      // For example, check if IMSI follows expected format
      if (imsi.length() < 10 || imsi.length() > 15) {
        log.error("Invalid IMSI format: " + imsi);
        return createULA(request, DIAMETER_ERROR_USER_UNKNOWN);
      }

      // Check if this subscriber exists in our database
      // Here you would typically query your subscriber database
      // For this example, we'll just check a simple condition
      if (imsi.startsWith("99999")) {
        log.error("Unknown subscriber with IMSI: " + imsi);
        return createULA(request, DIAMETER_ERROR_USER_UNKNOWN);
      }

      // Check if this subscriber has LTE/EPS subscription
      if (imsi.startsWith("88888")) {
        log.error("Subscriber has no EPS subscription: " + imsi);
        return createULA(request, DIAMETER_ERROR_UNKNOWN_EPS_SUBSCRIPTION);
      }

      // Process the RAT Type if present
      Avp ratTypeAvp = requestAvpSet.getAvp(RAT_TYPE);
      if (ratTypeAvp != null) {
        try {
          int ratType = ratTypeAvp.getInteger32();
          log.info("RAT Type: " + ratType);

          // You could process different RAT types differently
          // E.g., EUTRAN = 1004, UTRAN = 1000, etc.
        } catch (AvpDataException e) {
          log.error("Failed to read RAT-Type AVP", e);
        }
      }

      // Process ULR Flags if present
      Avp ulrFlagsAvp = requestAvpSet.getAvp(ULR_FLAGS, vendorID);
      if (ulrFlagsAvp != null) {
        try {
          long ulrFlags = ulrFlagsAvp.getUnsigned32();
          log.info("ULR Flags: " + ulrFlags);

          // Process specific flags
          boolean skipSubscriberData = (ulrFlags & 0x1) != 0;  // Bit 0
          boolean rauLuRegistration = (ulrFlags & 0x2) != 0;   // Bit 1
          boolean singleRegistration = (ulrFlags & 0x4) != 0;  // Bit 2
          boolean activeAPN = (ulrFlags & 0x8) != 0;          // Bit 3

          log.info("Skip Subscriber Data: " + skipSubscriberData);
          log.info("RAU/LU Registration: " + rauLuRegistration);
          log.info("Single Registration: " + singleRegistration);
          log.info("Active APN: " + activeAPN);
        } catch (AvpDataException e) {
          log.error("Failed to read ULR-Flags AVP", e);
        }
      }

      // If all checks pass, create a successful ULA response
      return createSuccessULA(request, imsi);

    } catch (AvpDataException e) {
      log.error("Failed to read User-Name AVP", e);
      return createULA(request, 5004); // DIAMETER_MISSING_AVP
    }
  }

  /**
   * Creates an Update-Location-Answer (ULA) with an error result code
   *
   * @param request The original ULR request
   * @param resultCode The error code to include in the response
   * @return The ULA message
   */
  private Answer createULA(Request request, int resultCode) {
    Answer answer = request.createAnswer(resultCode);
    AvpSet answerAvps = answer.getAvps();

    // Add origin AVPs required by Diameter base protocol
    answerAvps.addAvp(Avp.ORIGIN_HOST, stack.getMetaData().getLocalPeer().getUri().getFQDN(), true, false, true);
    answerAvps.addAvp(Avp.ORIGIN_REALM, stack.getMetaData().getLocalPeer().getRealmName(), true, false, true);

    dumpMessage(answer, true);
    return answer;
  }

  /**
   * Creates a successful Update-Location-Answer (ULA) with subscription data
   *
   * @param request The original ULR request
   * @param imsi The subscriber IMSI
   * @return The ULA message
   */
  private Answer createSuccessULA(Request request, String imsi) {
    Answer answer = request.createAnswer(DIAMETER_SUCCESS);
    AvpSet answerAvps = answer.getAvps();

    // Add origin AVPs required by Diameter base protocol
    answerAvps.addAvp(Avp.ORIGIN_HOST, stack.getMetaData().getLocalPeer().getUri().getFQDN(), true, false, true);
    answerAvps.addAvp(Avp.ORIGIN_REALM, stack.getMetaData().getLocalPeer().getRealmName(), true, false, true);

    // Add ULA-Flags AVP (vendor-specific)
    // Example: Set bit 0 for "Single-Registration-Indication"
    answerAvps.addAvp(ULA_FLAGS, 1L, vendorID, true, false, true);

    // Add subscription data (grouped AVP)
    try {
      AvpSet subscriptionDataAvp = answerAvps.addGroupedAvp(SUBSCRIPTION_DATA, vendorID, true, false);

      // Add MSISDN for this subscriber
      String msisdn = generateMsisdnFromImsi(imsi);
      byte[] msisdnBytes = msisdn.getBytes(StandardCharsets.UTF_8);
      subscriptionDataAvp.addAvp(MSISDN, msisdnBytes, vendorID, true, false);

      // Add other subscription data AVPs as needed
      // This would typically include APN configurations, QoS profiles, etc.
      // The actual AVPs depend on the specific 3GPP interfaces being implemented

      // Example: Add Access-Restriction-Data AVP (code 1426)
      subscriptionDataAvp.addAvp(1426, 0, vendorID, true, false, true);  // No restrictions

      // Example: Add Subscriber-Status AVP (code 1424)
      subscriptionDataAvp.addAvp(1424, 0, vendorID, true, false, true);  // SERVICE_GRANTED

      // Example: Add Network-Access-Mode AVP (code 1417)
      subscriptionDataAvp.addAvp(1417, 0, vendorID, true, false, true);  // PACKET_AND_CIRCUIT

      // You would add many more AVPs here for a real implementation

    } catch (Exception e) {
      log.error("Error creating subscription data AVPs", e);
    }

    dumpMessage(answer, true);

    // If this was the last expected message in the session, clean up
    this.session.release();
    this.session = null;

    return answer;
  }

  /**
   * Helper method to generate an MSISDN from IMSI
   * In a real implementation, this would query a database
   *
   * @param imsi The subscriber IMSI
   * @return A corresponding MSISDN
   */
  private String generateMsisdnFromImsi(String imsi) {
    // For testing, just use a simple transform
    // In production, you would look this up in your database
    return "1" + imsi.substring(imsi.length() - 10);
  }

  public Stack getStack() {
    return stack;
  }

  public SessionFactory getSessionFactory() {
    return factory;
  }

  public ApplicationId getAuthAppId() {
    return authAppId;
  }
}

