package org.example.controller;

import org.example.service.DiameterServerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/diameter/server")
public class DiameterServerController {

  @Autowired
  private DiameterServerService serverService;

  @CrossOrigin(origins = "http://localhost:3000")
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getServerStatus() {

    Map<String, Object> response = new HashMap<>();

    if (serverService.getStack() != null) {
      response.put("isRunning", true);
      response.put("message", "Diameter server is initialized and running.");
    } else {
      response.put("isRunning", false);
      response.put("message", "Diameter Server not initialized.");
    }

    return ResponseEntity.ok(response);
  }
}