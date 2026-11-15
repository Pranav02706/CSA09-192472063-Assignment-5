import java.sql.*;

public final class LocalDb {
    private LocalDb() { }

    static String url() {
        String url = System.getenv("CSA09_JDBC_URL");
        if (url == null || !url.matches("jdbc:mysql://(?:127\\.0\\.0\\.1|localhost):[0-9]{1,5}/csa09_persistence(?:\\?[^#]*)?")) {
            throw new IllegalArgumentException("Use a dedicated local csa09_persistence MySQL test database");
        }
        return url;
    }

    static String user() { return System.getenv("CSA09_DB_USER"); }
    static String password() { return System.getenv("CSA09_DB_PASSWORD"); }

    static Connection open() throws SQLException {
        return DriverManager.getConnection(url(), user(), password());
    }
}
