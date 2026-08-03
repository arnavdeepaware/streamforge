package io.streamforge.controlplane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boots the control-plane persistence and operational health service. */
@SpringBootApplication
public class ControlPlaneApplication {
  public static void main(String[] arguments) {
    SpringApplication.run(ControlPlaneApplication.class, arguments);
  }
}
