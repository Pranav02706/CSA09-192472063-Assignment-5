import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.math.BigDecimal;

public class PassLoader {
    /** Loads all passes in one transaction, in batches, and returns the number loaded. */
    public static int loadAll(Connection con, List<String[]> passes) throws SQLException {
        String sql = "INSERT INTO bus_pass(pass_id, reg_no, name, route, amount) VALUES (?, ?, ?, ?, ?)";
        boolean oldAutoCommit = con.getAutoCommit();
        if (!oldAutoCommit) throw new SQLException("Use a connection without a caller-owned transaction");
        con.setAutoCommit(false);
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            int count = 0;
            for (String[] p : passes) {
                ps.setString(1, p[0]);
                ps.setString(2, p[1]);
                ps.setString(3, p[2]);
                ps.setInt(4, Integer.parseInt(p[3]));
                ps.setBigDecimal(5, new BigDecimal(p[4]));
                ps.addBatch();
                count++;
                if (count % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
            con.commit();
            return count;
        } catch (SQLException | RuntimeException e) {
            try { con.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); throw e; }
            throw e;
        } finally {
            con.setAutoCommit(oldAutoCommit);
        }
    }
}
