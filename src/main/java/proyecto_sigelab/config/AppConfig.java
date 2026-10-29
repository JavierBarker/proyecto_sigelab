package proyecto_sigelab.config;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class AppConfig {
  private final Properties values = new Properties();

  public AppConfig() {
    Path p = Path.of("config/application.properties");
    if (Files.exists(p))
      try (var reader = Files.newBufferedReader(p)) {
        values.load(reader);
      } catch (IOException e) {
        throw new IllegalStateException("No se pudo leer la configuración.", e);
      }
  }

  public String get(String key, String fallback) {
    String env = System.getenv("SIGELAB_" + key.toUpperCase(Locale.ROOT).replace('.', '_'));
    return env != null ? env : values.getProperty(key, fallback);
  }

  public String required(String key) {
    String v = get(key, "");
    if (v.isBlank())
      throw new IllegalStateException("Configure " + key + " en config/application.properties.");
    return v;
  }
}
