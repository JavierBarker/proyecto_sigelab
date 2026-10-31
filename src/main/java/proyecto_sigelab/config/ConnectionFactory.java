package proyecto_sigelab.config;

import java.sql.*;
import java.util.Properties;

public final class ConnectionFactory {
  private final AppConfig config;

  public ConnectionFactory(AppConfig config) {
    this.config = config;
  }

  public Connection open() throws SQLException {
    Properties p = new Properties();
    p.setProperty("serverName", config.get("db.host", "localhost"));
    p.setProperty("portNumber", config.get("db.port", "14330"));
    p.setProperty("databaseName", config.get("db.name", "SIGELAB"));
    p.setProperty("user", config.required("db.user"));
    p.setProperty("password", config.required("db.password"));
    p.setProperty("encrypt", config.get("db.encrypt", "true"));
    p.setProperty("trustServerCertificate", config.get("db.trustServerCertificate", "true"));
    p.setProperty("loginTimeout", "8");
    p.setProperty("socketTimeout", "30000");
    return DriverManager.getConnection("jdbc:sqlserver://", p);
  }
}
