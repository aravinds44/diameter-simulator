package org.example.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.example.server.ExampleServer;
import org.jdiameter.api.Answer;
import org.jdiameter.api.Network;
import org.jdiameter.api.NetworkReqListener;
import org.jdiameter.api.Request;
import org.jdiameter.api.SessionFactory;
import org.jdiameter.api.Stack;
import org.jdiameter.api.InternalException;
import org.jdiameter.api.ApplicationAlreadyUseException;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class DiameterServerService implements NetworkReqListener {

  private static final Logger log = LoggerFactory.getLogger(DiameterServerService.class);

  private ExampleServer diameterServer;
  private Stack stack;
  private SessionFactory factory;

  public DiameterServerService() {
    this.diameterServer = new ExampleServer();
  }

  @PostConstruct
  public void init() {
    diameterServer.initStack();
    this.stack = diameterServer.getStack();
    this.factory = diameterServer.getSessionFactory();
    try {
      Network network = stack.unwrap(Network.class);
      network.addNetworkReqListener(this, diameterServer.getAuthAppId());
    } catch (InternalException | ApplicationAlreadyUseException e) {
      log.error("Error initializing Diameter server: ", e);
      // Consider throwing an exception here to prevent the application from starting
      throw new RuntimeException("Failed to initialize Diameter server", e); // Add this
    }
  }

  @PreDestroy
  public void destroy() {
    if (this.stack != null) {
      this.stack.destroy();
    }
  }

  @Override
  public Answer processRequest(Request request) {
    return diameterServer.processRequest(request);
  }

  public Stack getStack() {
    return stack;
  }
}