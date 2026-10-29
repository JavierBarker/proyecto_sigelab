package proyecto_sigelab;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import proyecto_sigelab.exception.BusinessException;
import proyecto_sigelab.integration.*;
import proyecto_sigelab.model.*;
import proyecto_sigelab.service.*;

class RulesTest {
  @Test
  void rejectsEmptyAndDuplicateLoans() {
    var now = LocalDateTime.now();
    assertThrows(BusinessException.class, () -> Rules.loan(List.of(), now, now.plusDays(1)));
    assertThrows(BusinessException.class, () -> Rules.loan(List.of(1, 1), now, now.plusDays(1)));
  }

  @Test
  void rejectsPastAndEqualDates() {
    var now = LocalDateTime.now();
    assertThrows(BusinessException.class, () -> Rules.loan(List.of(1), now, now));
    assertThrows(BusinessException.class, () -> Rules.loan(List.of(1), now, now.minusDays(1)));
  }

  @Test
  void onlyAvailableEquipmentCanBeLent() {
    for (var state : EstadoEquipo.values()) {
      Equipo e = new Equipo(1, "CAM-001", "Cámara", state);
      if (state == EstadoEquipo.DISPONIBLE) assertDoesNotThrow(() -> Rules.available(e));
      else assertThrows(BusinessException.class, () -> Rules.available(e));
    }
  }

  @Test
  void returnsRequireAnActiveLoanAndPendingMatchingDetail() {
    Prestamo p = new Prestamo(1, "Alumno", LocalDateTime.now(), "ACTIVO");
    assertThrows(
        BusinessException.class, () -> Rules.returnable(p, new DetallePrestamo(1, 1, 1, true)));
    assertThrows(
        BusinessException.class, () -> Rules.returnable(p, new DetallePrestamo(1, 2, 1, false)));
    assertThrows(
        BusinessException.class,
        () ->
            Rules.returnable(
                new Prestamo(1, "A", LocalDateTime.now(), "FINALIZADO"),
                new DetallePrestamo(1, 1, 1, false)));
  }

  @Test
  void maintenanceCannotTakeBorrowedOrRetiredEquipment() {
    for (var state : List.of(EstadoEquipo.PRESTADO, EstadoEquipo.MANTENIMIENTO, EstadoEquipo.BAJA))
      assertThrows(
          BusinessException.class,
          () -> Rules.maintenance(new Equipo(1, "A", "A", state), "CORRECTIVO"));
  }

  @Test
  void transitionResultsAreExact() {
    assertEquals(EstadoEquipo.DISPONIBLE, Rules.returnState("BUENO"));
    assertEquals(EstadoEquipo.DANADO, Rules.returnState("DANADO"));
    assertEquals(EstadoEquipo.DISPONIBLE, Rules.maintenanceResult("REPARADO"));
    assertEquals(EstadoEquipo.BAJA, Rules.maintenanceResult("NO_REPARABLE"));
  }

  @Test
  void inactiveAndNonAdminPermissionsAreRejected() {
    Usuario u = new Usuario(1, "A", "B", "a", "h", "e", "ENCARGADO", "ACTIVO");
    assertDoesNotThrow(() -> Rules.operator(u));
    assertThrows(BusinessException.class, () -> Rules.admin(u));
    assertThrows(
        BusinessException.class,
        () -> Rules.operator(new Usuario(1, "A", "B", "a", "h", "e", "ADMIN", "INACTIVO")));
  }

  @Test
  void facialResponseMustMatchAllChecks() throws Exception {
    ObjectMapper json = new ObjectMapper();
    String good = "{\"success\":true,\"matched\":true,\"livenessPassed\":true,\"userId\":1}";
    assertDoesNotThrow(() -> FacialClient.validateVerification(json.readTree(good), 1));
    for (String field : List.of("success", "matched", "livenessPassed")) {
      var d = json.readTree(good);
      ((com.fasterxml.jackson.databind.node.ObjectNode) d).put(field, false);
      assertThrows(BusinessException.class, () -> FacialClient.validateVerification(d, 1));
    }
    assertThrows(
        BusinessException.class, () -> FacialClient.validateVerification(json.readTree(good), 2));
    assertThrows(
        BusinessException.class, () -> FacialClient.validateVerification(json.readTree("{}"), 1));
  }

  @Test
  void qrRoundTripContainsOnlyInventoryCode() throws Exception {
    Path file = Files.createTempFile("sigelab-qr", ".png");
    try {
      QrService qr = new QrService();
      qr.generate("CAM-001", file);
      assertEquals("CAM-001", qr.read(file));
    } finally {
      Files.deleteIfExists(file);
    }
  }

  @Test
  void passwordsAreHashedAndValidated() {
    String h = AuthService.hashPassword("Test-password-123");
    assertTrue(org.mindrot.jbcrypt.BCrypt.checkpw("Test-password-123", h));
    assertFalse(org.mindrot.jbcrypt.BCrypt.checkpw("otra", h));
    assertThrows(BusinessException.class, () -> AuthService.hashPassword("corta"));
  }
}
