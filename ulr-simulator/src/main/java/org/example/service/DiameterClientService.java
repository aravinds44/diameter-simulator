package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.example.client.ExampleClient; // Import your existing client
import org.jdiameter.api.Answer;
import org.jdiameter.api.Request;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.Stack;
import org.jdiameter.api.EventListener;
import org.jdiameter.api.Session;
import org.jdiameter.api.AvpSet;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.RouteException;
import org.jdiameter.api.OverloadException;
import org.jdiameter.api.IllegalDiameterStateException;

import java.util.Map;
import java.util.HashMap;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class DiameterClientService implements EventListener<Request, Answer> {

  private static final Logger log = LoggerFactory.getLogger(DiameterClientService.class);

  private ExampleClient diameterClient;
  private Stack stack;
  private SessionFactory factory;
  private Session session;
  private boolean finished = false;

  public DiameterClientService() {
    this.diameterClient = new ExampleClient();
  }

  @PostConstruct
  public void init() {
    diameterClient.initStack();
    this.stack = diameterClient.getStack();
    this.factory = diameterClient.getSessionFactory();
  }

  @PreDestroy
  public void destroy() {
    if (this.stack != null) {
      this.stack.destroy();
    }
  }

  public Map<String, String> sendULR(String imsi, byte[] plmnId, int ratType, int ulrFlags) {
    try {
      this.session = this.factory.getNewSession("YourCustomSessionId-" + System.currentTimeMillis());
      Request ulr = this.session.createRequest(diameterClient.getCommandCode(), diameterClient.getAuthAppId(),
          diameterClient.getRealmName(), diameterClient.getServerHost());

      AvpSet requestAvps = ulr.getAvps();
      requestAvps.addAvp(diameterClient.getUSER_NAME(), imsi, false);
      requestAvps.addAvp(diameterClient.getVISITED_PLMN_ID(), plmnId, diameterClient.getVendorID(), false, false);
      requestAvps.addAvp(diameterClient.getULR_FLAGS(), ulrFlags, diameterClient.getVendorID(), false, false, true);
      requestAvps.addAvp(diameterClient.getRAT_TYPE(), ratType, diameterClient.getVendorID(), false, false, true);

      this.session.send(ulr, this);
      diameterClient.dumpMessage(ulr, true);

      Map<String, String> response = new HashMap<>();
      response.put("message", "ULR Sent Successfully");
      return response;

    } catch (InternalException | IllegalDiameterStateException | RouteException | OverloadException e) {
      log.error("Error sending ULR: ", e);
      Map<String, String> errorResponse = new HashMap<>();
      errorResponse.put("message", "Error sending ULR: " + e.getMessage());
      return errorResponse;
    }
  }

  @Override
  public void receivedSuccessMessage(Request request, Answer answer) {
    diameterClient.handleSuccessAnswer(request, answer, this.session);
    this.finished = true;
  }

  @Override
  public void timeoutExpired(Request request) {
    diameterClient.timeoutExpired(request);
    this.finished = true;
  }

  public boolean isFinished() {
    return finished;
  }

  public Stack getStack() {
    return stack;
  }
}