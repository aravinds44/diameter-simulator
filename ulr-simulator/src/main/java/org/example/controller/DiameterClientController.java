package org.example.controller;

import org.example.service.DiameterClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
import java.util.Map;
import java.util.HashMap;


@RestController
@RequestMapping("/diameter/client")
public class DiameterClientController {

  @Autowired
  private DiameterClientService clientService;

  @CrossOrigin(origins = "http://localhost:3000")
  @GetMapping("/status")
  public ResponseEntity<Map<String, Object>> getClientStatus() {
    Map<String, Object> response = new HashMap<>();

    if (clientService.getStack() != null) {
      response.put("isRunning", true);
      response.put("message", "Diameter client is initialized and running.");
    } else {
      response.put("isRunning", false);
      response.put("message", "Diameter Client not initialized.");
    }

    return ResponseEntity.ok(response);
  }

  @CrossOrigin(origins = "http://localhost:3000")
  @PostMapping("/sendULR")
  public ResponseEntity<Map<String, String>> sendULR(@RequestBody Map<String, Object> data) {
    String imsi = (String) data.get("imsi");
    String plmnId = (String) data.get("plmnId");
    int ratType = (int) data.get("ratType");
    int ulrFlags = (int) data.get("ulrFlags");

    byte[] plmnIdBytes = convertHexStringToByteArray(plmnId);
    Map<String, String> response = clientService.sendULR(imsi, plmnIdBytes, ratType, ulrFlags);

    // Return the response wrapped in ResponseEntity
    return ResponseEntity.ok(response);
  }

  // Utility method to convert hex string to byte array
  private byte[] convertHexStringToByteArray(String hexString) {
    int len = hexString.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
          + Character.digit(hexString.charAt(i + 1), 16));
    }
    return data;
  }
}