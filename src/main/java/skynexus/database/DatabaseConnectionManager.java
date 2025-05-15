package skynexus.database;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import skynexus.SkyNexus;
import skynexus.service.SecurityService;
import skynexus.util.Config;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Vereinfachter Connection Pool für SkyNexus.
 * Verwaltet Datenbankverbindungen für die Wiederverwendung.
 */
public class DatabaseConnectionManager {
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConnectionManager.class);
    private static final String COMPONENT_NAME = "DatabaseConnectionManager";
    private static DatabaseConnectionManager instance;

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final List<Connection> connectionPool = new ArrayList<>();

    private String url;
    private String username;
    private String password;
    private int maxPoolSize;
    private int connectionTimeout;
    private int connectionCheckInterval;
    private int activeConnections = 0;
    private final CompletableFuture<Void> readyFuture;

    private ScheduledExecutorService scheduler;

    private DatabaseConnectionManager() {
        readyFuture = SkyNexus.ApplicationReadyManager.getInstance().registerRequiredComponent(COMPONENT_NAME);

        try {
            loadConfig();
            initializeScheduler();
            initializeConnectionAsync();
        } catch (Exception e) {
            logger.error("Fehler bei der Initialisierung: {}", e.getMessage());
            SkyNexus.ApplicationReadyManager.getInstance().setComponentFailed(COMPONENT_NAME, e);
        }
    }

    public static synchronized DatabaseConnectionManager getInstance() {
        if (instance == null) {
            instance = new DatabaseConnectionManager();
        }
        return instance;
    }

    private void initializeConnectionAsync() {
        Thread initializer = new Thread(() -> {
            try {
                logger.info("Starte Datenbankinitialisierung");

                Connection testConn = createConnection();

                DatabaseMetaData metaData = testConn.getMetaData();
                logger.debug("Datenbankverbindung hergestellt: {} {}",
                        metaData.getDatabaseProductName(),
                        metaData.getDatabaseProductVersion());

                connectionPool.add(testConn);

                connected.set(true);
                SkyNexus.ApplicationReadyManager.getInstance().setComponentReady(COMPONENT_NAME);

                logger.info("Datenbankinitialisierung abgeschlossen");
            } catch (SQLException e) {
                logger.error("Datenbankinitialisierung fehlgeschlagen: {}", e.getMessage());
                SkyNexus.ApplicationReadyManager.getInstance().setComponentFailed(COMPONENT_NAME, e);
                connected.set(false);
            }
        });

        initializer.setName("DB-Initializer");
        initializer.setDaemon(true);
        initializer.start();
    }

    private void loadConfig() {
        url = Config.getDbProperty("db.url", "jdbc:mariadb://localhost:3306/SkyNexus");
        username = Config.getDbProperty("db.user", "root");

        boolean isEncrypted = Boolean.parseBoolean(Config.getDbProperty("db.password.encrypted", "false"));
        String passwordValue = Config.getDbProperty("db.password", "");

        if (isEncrypted && !passwordValue.isEmpty()) {
            try {
                password = SecurityService.getInstance().decrypt(passwordValue);
            } catch (Exception e) {
                logger.error("Fehler beim Entschlüsseln des Passworts: {}", e.getMessage());
                password = "";
            }
        } else {
            password = passwordValue;
        }

        maxPoolSize = Config.getDbPropertyInt("db.maxPoolSize", 7);
        connectionTimeout = Config.getDbPropertyInt("db.connectionTimeout", 5000);
        connectionCheckInterval = Config.getDbPropertyInt("db.connection.check.interval", 10);

        logger.debug("Datenbankkonfiguration geladen: {}", url);
    }

    private void initializeScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        scheduler = Executors.newScheduledThreadPool(1);

        SkyNexus.ApplicationReadyManager.getInstance().runWhenReady(() -> {
            scheduler.scheduleAtFixedRate(
                    this::checkConnections,
                    0,
                    connectionCheckInterval,
                    TimeUnit.SECONDS
            );
        });
    }

    private synchronized void checkConnections() {
        if (connectionPool.isEmpty()) {
            return;
        }

        List<Connection> invalidConnections = new ArrayList<>();

        for (Connection conn : connectionPool) {
            try {
                if (conn == null || conn.isClosed() || !conn.isValid(2)) {
                    invalidConnections.add(conn);
                }
            } catch (SQLException e) {
                logger.warn("Fehler bei Verbindungsprüfung: {}", e.getMessage());
                invalidConnections.add(conn);
            }
        }

        if (!invalidConnections.isEmpty()) {
            logger.debug("Entferne {} ungültige Verbindungen", invalidConnections.size());

            for (Connection conn : invalidConnections) {
                connectionPool.remove(conn);
                closeConnectionSilently(conn);
            }
        }
    }

    public synchronized Connection getConnection() throws SQLException {
        try {
            readyFuture.join();
        } catch (Exception e) {
            throw new SQLException("Datenbankverbindung nicht verfügbar: " + e.getMessage(), e);
        }

        if (!connectionPool.isEmpty()) {
            Connection conn = connectionPool.removeLast();

            if (conn != null && !conn.isClosed() && conn.isValid(2)) {
                activeConnections++;
                return conn;
            } else {
                closeConnectionSilently(conn);
            }
        }

        try {
            Connection conn = createConnection();
            activeConnections++;
            logger.debug("Neue Datenbankverbindung erstellt");
            return conn;
        } catch (SQLException e) {
            connected.set(false);
            logger.error("Fehler beim Herstellen der Verbindung: {}", e.getMessage());
            throw e;
        }
    }

    private Connection createConnection() throws SQLException {
        Connection conn = DriverManager.getConnection(url, username, password);
        if (conn != null) {
            conn.setNetworkTimeout(null, connectionTimeout);
            conn.setAutoCommit(true);

            connected.set(true);
            return conn;
        } else {
            throw new SQLException("Verbindungserstellung fehlgeschlagen: Null-Connection");
        }
    }

    public synchronized void releaseConnection(Connection conn) {
        if (conn == null) {
            return;
        }

        try {
            if (!conn.isClosed() && conn.isValid(2) && connectionPool.size() < maxPoolSize) {
                if (!conn.getAutoCommit()) {
                    conn.setAutoCommit(true);
                }

                connectionPool.add(conn);
            } else {
                conn.close();
            }
        } catch (SQLException e) {
            logger.warn("Fehler beim Freigeben der Verbindung: {}", e.getMessage());
            closeConnectionSilently(conn);
        } finally {
            if (activeConnections > 0) {
                activeConnections--;
            }
        }
    }

    private void closeConnectionSilently(Connection conn) {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
            }
        } catch (SQLException e) {
            logger.warn("Fehler beim Schließen der Verbindung: {}", e.getMessage());
        }
    }

    public boolean isConnected() {
        if (!SkyNexus.ApplicationReadyManager.getInstance().isApplicationReady()) {
            return false;
        }

        Connection testConn = null;
        try {
            testConn = getConnection();
            boolean isValid = testConn != null && testConn.isValid(2);
            return isValid;
        } catch (SQLException e) {
            logger.error("Verbindungsprüfung fehlgeschlagen: {}", e.getMessage());
            connected.set(false);
            return false;
        } finally {
            if (testConn != null) {
                releaseConnection(testConn);
            }
        }
    }

    public synchronized void shutdown() {
        logger.info("Datenbankverbindungen werden geschlossen");

        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
        }

        for (Connection conn : connectionPool) {
            closeConnectionSilently(conn);
        }

        connectionPool.clear();
        activeConnections = 0;

        logger.info("Datenbankverbindungen geschlossen");
    }

    public void reloadConfig() {
        logger.debug("Lade Datenbankkonfiguration neu");
        loadConfig();
        initializeScheduler();
    }

    public String getConnectionStats() {
        return String.format(
                "Verbindungen aktiv: %d, im Pool: %d, Max: %d",
                activeConnections, connectionPool.size(), maxPoolSize);
    }

    public CompletableFuture<Void> getReadyFuture() {
        return readyFuture;
    }

    public static class ConnectionScope implements AutoCloseable {
        private static final Logger logger = LoggerFactory.getLogger(ConnectionScope.class);

        private final Connection connection;
        private final DatabaseConnectionManager connectionManager;
        private boolean isReleased = false;

        public ConnectionScope(Connection connection, DatabaseConnectionManager connectionManager) {
            this.connection = connection;
            this.connectionManager = connectionManager;
        }

        public static ConnectionScope acquire() throws SQLException {
            DatabaseConnectionManager manager = getInstance();
            Connection connection = manager.getConnection();
            return new ConnectionScope(connection, manager);
        }

        public Connection getConnection() {
            return connection;
        }

        public <T> T execute(DatabaseHelper.SQLFunction<Connection, T> function) throws SQLException {
            try {
                return function.apply(connection);
            } catch (SQLException e) {
                throw e;
            } catch (Exception e) {
                throw new SQLException("Fehler bei Datenbankoperation", e);
            }
        }

        @Override
        public void close() {
            if (!isReleased) {
                connectionManager.releaseConnection(connection);
                isReleased = true;
            }
        }
    }

    public ConnectionScope createConnectionScope() throws SQLException {
        Connection connection = getConnection();
        return new ConnectionScope(connection, this);
    }
}
