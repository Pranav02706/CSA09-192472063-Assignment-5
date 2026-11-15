import java.sql.*;

public final class WalletTest {
    private static void move(Connection c, int from, int to, boolean fail) throws SQLException {
        c.setAutoCommit(false);
        try (PreparedStatement lock = c.prepareStatement("SELECT balance FROM wallet WHERE id=? FOR UPDATE");
             PreparedStatement update = c.prepareStatement("UPDATE wallet SET balance=balance+? WHERE id=?")) {
            for (int id : new int[]{Math.min(from,to), Math.max(from,to)}) {
                lock.setInt(1,id);
                try (ResultSet rows = lock.executeQuery()) {
                    if (!rows.next() || (id == from && rows.getLong(1) < 50000)) throw new SQLException("Insufficient balance");
                }
            }
            update.setLong(1, -50000); update.setInt(2,from);
            if (update.executeUpdate() != 1) throw new SQLException("Debit failed");
            if (fail) throw new SQLException("Injected after debit");
            update.setLong(1, 50000); update.setInt(2,to);
            if (update.executeUpdate() != 1) throw new SQLException("Credit failed");
            c.commit();
        } catch (SQLException e) {
            try { c.rollback(); } catch (SQLException rollback) { e.addSuppressed(rollback); }
            throw e;
        } finally { c.setAutoCommit(true); }
    }

    private static void verify(Connection c, long first, long second) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT balance FROM wallet WHERE id IN (101,102) ORDER BY id")) {
            if (!rs.next() || rs.getLong(1) != first || !rs.next() || rs.getLong(1) != second || rs.next()) {
                throw new AssertionError("Balances differ");
            }
        }
        System.out.printf("MySQL balances observed in paise: %d and %d%n", first, second);
    }

    public static void main(String[] args) throws SQLException {
        try (Connection c = LocalDb.open(); Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM wallet WHERE id IN (101,102)");
            try {
                s.executeUpdate("INSERT INTO wallet(id,balance) VALUES (101,100000),(102,20000)");
                try { move(c,101,102,true); throw new AssertionError("Injected error missing"); }
                catch (SQLException expected) {
                    if (!expected.getMessage().equals("Injected after debit")) throw expected;
                    System.out.println(expected.getMessage());
                }
                verify(c,100000,20000);
                move(c,101,102,false);
                verify(c,50000,70000);
            } finally { s.executeUpdate("DELETE FROM wallet WHERE id IN (101,102)"); }
        }
    }
}
