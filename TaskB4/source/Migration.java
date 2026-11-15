import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;

public final class Migration {
    static int migrate(Connection c, Path input, Path rejectionLog) throws IOException, SQLException {
        if (!c.getAutoCommit()) throw new SQLException("Use a dedicated connection");
        c.setAutoCommit(false);
        String sql = "INSERT INTO bus_pass(pass_id,reg_no,name,route,amount,boarding_stop,valid_until) VALUES(?,?,?,?,?,?,?)";
        int accepted = 0, pending = 0;
        Set<String> ids = new HashSet<>();
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8);
             BufferedWriter rejected = Files.newBufferedWriter(rejectionLog, StandardCharsets.UTF_8);
             PreparedStatement statement = c.prepareStatement(sql)) {
            int number = 0;
            for (String line; (line = reader.readLine()) != null;) {
                number++;
                FullPassStore.Pass pass;
                try {
                    pass = FullPassStore.Pass.parse(line);
                    if (!ids.add(pass.id.toUpperCase(java.util.Locale.ROOT))) {
                        throw new IllegalArgumentException("Duplicate in input");
                    }
                } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
                    rejected.write("Line " + number + ": " + invalid.getMessage());
                    rejected.newLine();
                    continue;
                }
                statement.setString(1, pass.id); statement.setString(2, pass.registration);
                statement.setString(3, pass.name); statement.setInt(4, pass.route);
                statement.setBigDecimal(5, pass.amount); statement.setString(6, pass.stop);
                statement.setDate(7, Date.valueOf(pass.validUntil));
                statement.addBatch();
                accepted++;
                if (++pending == 500) { statement.executeBatch(); pending = 0; }
            }
            if (pending > 0) statement.executeBatch();
            rejected.flush();
            c.commit();
            return accepted;
        } catch (SQLException | IOException | RuntimeException e) {
            try { c.rollback(); }
            catch (SQLException rollback) {
                e.addSuppressed(rollback);
                try { c.close(); } catch (SQLException close) { e.addSuppressed(close); }
            }
            throw e;
        } finally {
            if (!c.isClosed()) c.setAutoCommit(true);
        }
    }

    static void metadata(Connection c) throws SQLException {
        DatabaseMetaData md = c.getMetaData();
        System.out.println("Product: " + md.getDatabaseProductName() + " " + md.getDatabaseProductVersion());
        System.out.println("Driver: " + md.getDriverName() + " " + md.getDriverVersion());
        try (ResultSet tables = md.getTables(c.getCatalog(), null, "%", new String[]{"TABLE"})) {
            while (tables.next()) System.out.println("Table: " + tables.getString("TABLE_NAME"));
        }
    }

    private static void fixture(Connection c) throws IOException, SQLException {
        Path directory = Files.createTempDirectory("migration-fixture");
        Path input = directory.resolve("passes.txt");
        Path log = directory.resolve("rejected.txt");
        try {
            Files.write(input, java.util.Arrays.asList(FullPassStore.sample(123).encode(), "BAD|ROW",
                    FullPassStore.sample(123).encode(), FullPassStore.sample(124).encode()),
                    StandardCharsets.UTF_8);
            try (Statement s = c.createStatement()) {
                s.executeUpdate("DELETE FROM bus_pass WHERE pass_id IN ('P123','P124')");
                try {
                    int rows = migrate(c, input, log);
                    java.util.List<String> errors = Files.readAllLines(log, StandardCharsets.UTF_8);
                    if (rows != 2 || errors.size() != 2) throw new AssertionError();
                    try (ResultSet rs = s.executeQuery("SELECT name,boarding_stop,valid_until FROM bus_pass WHERE pass_id IN ('P123','P124')")) {
                        for (int i = 0; i < 2; i++) {
                            if (!rs.next() || !rs.getString(1).equals("Asha|Rao\\Sample")
                                    || !rs.getString(2).equals("North Gate")
                                    || !rs.getDate(3).toString().equals("2027-03-31")) throw new AssertionError("Migration field");
                        }
                        if (rs.next()) throw new AssertionError("Extra row");
                    }
                    for (String error : errors) System.out.println(error);
                    System.out.println("Migrated and verified 2 valid rows; 2 validation failures.");
                } finally { s.executeUpdate("DELETE FROM bus_pass WHERE pass_id IN ('P123','P124')"); }
            }
        } finally { Files.deleteIfExists(input); Files.deleteIfExists(log); Files.deleteIfExists(directory); }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 0 && args.length != 2) throw new IllegalArgumentException("Supply input and rejection paths");
        try (Connection c = LocalDb.open()) {
            metadata(c);
            if (args.length == 0) fixture(c);
            else System.out.println("Migrated " + migrate(c, Paths.get(args[0]), Paths.get(args[1])) + " valid rows");
        }
    }
}
