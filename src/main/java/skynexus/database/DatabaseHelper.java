package skynexus.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;

/**
 * Hilfesklasse für den Zugriff auf die Datenbank.
 * Vereinfacht den Umgang mit Datenbankverbindungen und -abfragen.
 */
public final class DatabaseHelper {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseHelper.class);
    private static final DatabaseConnectionManager connectionManager = DatabaseConnectionManager.getInstance();

    private DatabaseHelper() {
    }

    /**
     * Führt eine SQL-Abfrage mit Parametern aus und verarbeitet die Ergebnisse.
     */
    public static <T> T executeQuery(String sql, ResultSetHandler<T> handler, Object... parameters) throws SQLException {
        Connection conn = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            conn = connectionManager.getConnection();
            pstmt = conn.prepareStatement(sql);
            setParameters(pstmt, parameters);

            long startTime = System.currentTimeMillis();
            rs = pstmt.executeQuery();
            long duration = System.currentTimeMillis() - startTime;

            if (duration > 1000) {
                logger.warn("Langsame SQL-Abfrage ({}ms): {}", duration, sql);
            } else {
                logger.debug("SQL-Abfrage ausgeführt in {}ms", duration);
            }

            return handler.handle(rs);
        } catch (SQLException e) {
            logger.error("Fehler bei SQL-Abfrage: {} - {}", sql, e.getMessage());
            throw e;
        } finally {
            closeResources(rs, pstmt, conn);
        }
    }

    /**
     * Setzt Parameter für ein PreparedStatement.
     */
    private static void setParameters(PreparedStatement pstmt, Object... parameters) throws SQLException {
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i] == null) {
                pstmt.setNull(i + 1, Types.NULL);
            } else {
                pstmt.setObject(i + 1, parameters[i]);
            }
        }
    }

    /**
     * Schließt alle übergebenen Ressourcen.
     */
    private static void closeResources(ResultSet rs, Statement stmt, Connection conn) {
        if (rs != null) {
            try {
                rs.close();
            } catch (SQLException e) {
                logger.debug("Fehler beim Schließen des ResultSet: {}", e.getMessage());
            }
        }
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                logger.debug("Fehler beim Schließen des Statement: {}", e.getMessage());
            }
        }
        if (conn != null) {
            connectionManager.releaseConnection(conn);
        }
    }

    /**
     * Funktionales Interface zur Verarbeitung eines ResultSet.
     */
    @FunctionalInterface
    public interface ResultSetHandler<T> {
        T handle(ResultSet rs) throws SQLException;
    }

    /**
     * Funktionales Interface zur Umwandlung eines ResultSet-Datensatzes in ein Objekt.
     */
    @FunctionalInterface
    public interface RowMapper<T> {
        T mapRow(ResultSet rs, int rowNum) throws SQLException;
    }

    /**
     * Funktionales Interface für SQL-Funktionen.
     */
    @FunctionalInterface
    public interface SQLFunction<T, R> {
        R apply(T t) throws SQLException;
    }

    /**
     * Funktionales Interface für SQL-Consumer.
     */
    @FunctionalInterface
    public interface SQLConsumer<T> {
        void accept(T t) throws SQLException;
    }
}
