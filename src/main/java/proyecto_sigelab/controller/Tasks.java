package proyecto_sigelab.controller;

import java.awt.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.logging.*;
import javax.swing.*;
import proyecto_sigelab.exception.*;
import proyecto_sigelab.view.Forms;

public final class Tasks {
  private Tasks() {}

  public static <T> void run(JFrame owner, Callable<T> work, Consumer<T> done) {
    if (Boolean.TRUE.equals(owner.getRootPane().getClientProperty("busy"))) return;
    owner.getRootPane().putClientProperty("busy", true);
    JPanel cover = new JPanel();
    cover.setOpaque(false);
    cover.addMouseListener(new java.awt.event.MouseAdapter() {});
    cover.addKeyListener(new java.awt.event.KeyAdapter() {});
    cover.setFocusable(true);
    owner.setGlassPane(cover);
    cover.setVisible(true);
    cover.requestFocusInWindow();
    owner.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    new SwingWorker<T, Void>() {
      protected T doInBackground() throws Exception {
        return work.call();
      }

      protected void done() {
        owner.getRootPane().putClientProperty("busy", false);
        cover.setVisible(false);
        owner.setCursor(Cursor.getDefaultCursor());
        try {
          done.accept(get());
        } catch (Exception ex) {
          Throwable cause = ex instanceof ExecutionException ? ex.getCause() : ex;
          if (cause instanceof DataAccessException)
            Logger.getLogger("sigelab").log(Level.WARNING, "Fallo de base de datos", cause);
          Forms.error(
              owner,
              cause instanceof BusinessException
                      || cause instanceof IllegalArgumentException
                      || cause instanceof IllegalStateException
                      || cause instanceof DataAccessException
                  ? cause.getMessage()
                  : "Ocurrió un error inesperado. Revise la configuración e inténtelo de nuevo.");
        }
      }
    }.execute();
  }

  public static void guarded(JFrame owner, Runnable action) {
    try {
      action.run();
    } catch (RuntimeException e) {
      Forms.error(owner, e.getMessage());
    }
  }
}
