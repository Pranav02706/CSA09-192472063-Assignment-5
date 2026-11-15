import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

public final class JdbcBenchmark {
    private static final String INSERT = "INSERT INTO bus_pass(pass_id,reg_no,name,route,amount) VALUES (?,?,?,?,?)";

    static int count(Connection c) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet rows = s.executeQuery("SELECT COUNT(*) FROM bus_pass")) {
            rows.next();
            return rows.getInt(1);
        }
    }

    static void clear(Connection c) throws SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Expected clean benchmark connection");
        try (Statement s = c.createStatement()) { s.executeUpdate("TRUNCATE TABLE bus_pass"); }
    }

    private static List<String[]> fixture(int n) {
        List<String[]> rows = new ArrayList<>(n);
        for (int id = 0; id < n; id++) {
            rows.add(new String[]{"P" + id, Integer.toString(192300000 + id), "Fixture " + id, "7", "3800.00"});
        }
        return rows;
    }

    private static void bind(PreparedStatement s, String[] p) throws SQLException {
        s.setString(1,p[0]); s.setString(2,p[1]); s.setString(3,p[2]);
        s.setInt(4,Integer.parseInt(p[3])); s.setBigDecimal(5,new BigDecimal(p[4]));
    }

    private static void insert(Connection c, List<String[]> rows, String mode, boolean auto) throws SQLException {
        c.setAutoCommit(auto);
        try {
            if (mode.equals("statement")) {
                try (Statement s = c.createStatement()) {
                    for (String[] p : rows) {
                        // This path receives only internally generated alphanumeric fixture data.
                        s.executeUpdate("INSERT INTO bus_pass(pass_id,reg_no,name,route,amount) VALUES ('"
                                + p[0] + "','" + p[1] + "','" + p[2] + "'," + p[3] + "," + p[4] + ")");
                    }
                }
            } else {
                try (PreparedStatement s = c.prepareStatement(INSERT)) {
                    int pending = 0;
                    for (String[] p : rows) {
                        bind(s, p);
                        if (mode.equals("prepared")) s.executeUpdate();
                        else {
                            s.addBatch();
                            if (++pending == 500) { s.executeBatch(); pending = 0; }
                        }
                    }
                    if (pending > 0) s.executeBatch();
                }
            }
            if (!auto) c.commit();
        } catch (SQLException | RuntimeException e) {
            if (!auto) c.rollback();
            throw e;
        } finally { c.setAutoCommit(true); }
    }

    private static void verifyTen(Connection c) throws SQLException {
        try (Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT pass_id,reg_no,name,route,amount FROM bus_pass ORDER BY pass_id")) {
            ResultSetMetaData md = rs.getMetaData();
            int[] expected = {Types.VARCHAR,Types.VARCHAR,Types.VARCHAR,Types.INTEGER,Types.DECIMAL};
            for (int i = 1; i <= expected.length; i++) {
                if (md.getColumnType(i) != expected[i-1]) throw new AssertionError(md.getColumnTypeName(i));
                System.out.print(md.getColumnLabel(i) + "=" + md.getColumnTypeName(i) + " ");
            }
            System.out.println();
            for (String[] row : fixture(10)) {
                if (!rs.next() || !rs.getString(1).equals(row[0]) || !rs.getString(2).equals(row[1])
                        || !rs.getString(3).equals(row[2]) || rs.getInt(4) != 7
                        || rs.getBigDecimal(5).compareTo(new BigDecimal(row[4])) != 0) {
                    throw new AssertionError("Row mismatch");
                }
            }
            if (rs.next()) throw new AssertionError("Extra row");
        }
    }

    private static void failure(Connection c, boolean auto) throws SQLException {
        clear(c);
        List<String[]> data = fixture(1000);
        data.get(500)[0] = data.get(0)[0];
        try { insert(c,data,"batch",auto); throw new AssertionError("Duplicate should fail"); }
        catch (SQLException expected) {
            if (expected.getErrorCode() != 1062) throw expected;
            int remaining = count(c);
            if (auto ? remaining < 500 : remaining != 0) throw new AssertionError(remaining);
            System.out.printf("Injected duplicate, autocommit=%s, SQLState=%s, persisted=%d%n",
                    auto, expected.getSQLState(), remaining);
        }
    }

    public static void main(String[] args) throws SQLException {
        boolean quick = args.length == 1 && args[0].equals("quick");
        try (Connection c = LocalDb.open()) {
            if (quick) {
                for (String mode : new String[]{"statement","prepared","batch"}) {
                    clear(c);
                    insert(c,fixture(10),mode,false);
                    System.out.print(mode + ": ");
                    verifyTen(c);
                }
                failure(c,true);
                failure(c,false);
                clear(c);
                List<String[]> data = fixture(1000);
                data.get(500)[4] = "bad-amount";
                try { PassLoader.loadAll(c,data); throw new AssertionError(); }
                catch (NumberFormatException expected) {
                    if (count(c) != 0) throw new AssertionError("Failed rollback");
                    System.out.println("PassLoader invalid amount halfway, persisted=0");
                }
                return;
            }

            System.out.println("records,mode,auto_commit,repetition,ns,verified_count");
            for (int size : new int[]{10,1000,10000,100000}) {
                List<String[]> data = fixture(size);
                String[] modes = {"statement","prepared","batch","batch","loader"};
                for (int run = 0; run <= 3; run++) {
                    for (int step = 0; step < modes.length; step++) {
                        int m = (step + run) % modes.length;
                        String mode = modes[m];
                        boolean auto = m == 3;
                        clear(c);
                        long start = System.nanoTime();
                        if (mode.equals("loader")) {
                            if (PassLoader.loadAll(c,data) != size) throw new AssertionError();
                        } else insert(c,data,mode,auto);
                        long elapsed = System.nanoTime() - start;
                        int actual = count(c);
                        if (actual != size) throw new AssertionError(actual);
                        if (run > 0) System.out.printf("%d,%s,%b,%d,%d,%d%n",size,mode,auto,run,elapsed,actual);
                    }
                }
            }
        }
    }
}
