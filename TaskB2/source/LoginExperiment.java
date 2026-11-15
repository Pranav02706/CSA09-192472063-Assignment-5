import java.sql.*;

public final class LoginExperiment {
    private static boolean unsafe(Connection c, String id, String password) throws SQLException {
        String sql = "SELECT * FROM users WHERE id='" + id + "' AND pwd='" + password + "'";
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(sql)) { return rs.next(); }
    }

    private static boolean safe(Connection c, String id, String password) throws SQLException {
        try (PreparedStatement s = c.prepareStatement("SELECT id FROM users WHERE id=? AND pwd=?")) {
            s.setString(1, id); s.setString(2, password);
            try (ResultSet rs = s.executeQuery()) { return rs.next(); }
        }
    }

    private static void compare(Connection c, String label, String id, String password,
            boolean expectedUnsafe, boolean expectedSafe) throws SQLException {
        boolean vulnerable = unsafe(c,id,password);
        boolean parameterized = safe(c,id,password);
        System.out.printf("%s unsafe=%s prepared=%s%n", label,vulnerable,parameterized);
        if (vulnerable != expectedUnsafe || parameterized != expectedSafe) throw new AssertionError(label);
    }

    public static void main(String[] args) throws SQLException {
        try (Connection c = LocalDb.open()) {
            try (Statement s = c.createStatement()) {
                s.executeUpdate("INSERT INTO users(id,pwd) VALUES ('demo_user','fixture_value') "
                        + "ON DUPLICATE KEY UPDATE pwd='fixture_value'");
            }
            compare(c,"correct fixture","demo_user","fixture_value",true,true);
            compare(c,"wrong password","demo_user","wrong",false,false);
            compare(c,"crafted input","missing' OR 1=1 -- ","wrong",true,false);
        }
    }
}
