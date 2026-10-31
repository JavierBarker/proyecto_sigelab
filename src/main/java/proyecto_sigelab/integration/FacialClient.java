package proyecto_sigelab.integration;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import proyecto_sigelab.config.AppConfig;
import proyecto_sigelab.exception.BusinessException;

public final class FacialClient {
  private final AppConfig config;
  private final ObjectMapper json = new ObjectMapper();
  private final HttpClient http =
      HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(5)).build();

  public FacialClient(AppConfig config) {
    this.config = config;
  }

  private JsonNode call(String path, String method, Object body) {
    try {
      URI uri = URI.create(config.get("facial.url", "http://127.0.0.1:8765") + path);
      if (!"http".equals(uri.getScheme())
          || !("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost())))
        throw new BusinessException("El servicio facial debe ejecutarse localmente.");
      var req =
          HttpRequest.newBuilder(uri)
              .timeout(Duration.ofSeconds(240))
              .header("X-API-Key", config.required("facial.token"))
              .header("Content-Type", "application/json");
      req.method(
          method,
          body == null
              ? HttpRequest.BodyPublishers.noBody()
              : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
      var res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
      JsonNode data = json.readTree(res.body());
      if (res.statusCode() < 200 || res.statusCode() >= 300)
        throw new BusinessException(
            data.path("detail").isTextual()
                ? data.path("detail").asText()
                : "El servicio facial rechazó la solicitud.");
      return data;
    } catch (BusinessException e) {
      throw e;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException("Sesión facial interrumpida.");
    } catch (Exception e) {
      throw new BusinessException(
          "No se pudo completar la comunicación facial. Inicie el servicio, revise el token y la"
              + " cámara.");
    }
  }

  public boolean enrolled(int id) {
    JsonNode data = call("/face/status/" + id, "GET", null);
    if (data.path("userId").asInt() != id || !data.path("enrolled").isBoolean())
      throw new BusinessException("Respuesta facial inválida.");
    return data.path("enrolled").asBoolean();
  }

  public void enroll(int id, boolean replace) {
    JsonNode d = call("/face/enroll", "POST", Map.of("userId", id, "replace", replace));
    if (!d.path("success").asBoolean()
        || d.path("userId").asInt() != id
        || !d.path("livenessPassed").asBoolean())
      throw new BusinessException("No se completó el registro facial.");
  }

  public void verify(int id) {
    JsonNode d = call("/face/verify", "POST", Map.of("userId", id));
    validateVerification(d, id);
  }

  public static void validateVerification(JsonNode d, int id) {
    if (d == null
        || !d.path("success").asBoolean(false)
        || !d.path("matched").asBoolean(false)
        || !d.path("livenessPassed").asBoolean(false)
        || d.path("userId").asInt(-1) != id)
      throw new BusinessException(
          "Rostro no reconocido o respuesta facial inválida. Acceso denegado.");
  }
}
