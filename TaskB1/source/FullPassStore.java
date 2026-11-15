import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;

public final class FullPassStore {
    static final class Pass {
        final String id, registration, name, stop;
        final int route;
        final LocalDate validUntil;
        final BigDecimal amount;

        Pass(String id, String registration, String name, int route, String stop, LocalDate validUntil,
                BigDecimal amount) {
            if (!id.matches("[A-Za-z0-9-]{1,24}") || !registration.matches("[0-9]{1,20}")) {
                throw new IllegalArgumentException("Invalid identifiers");
            }
            if (name.trim().isEmpty() || stop.trim().isEmpty() || name.length() > 100 || stop.length() > 100
                    || name.chars().anyMatch(Character::isISOControl) || stop.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("Invalid name or stop");
            }
            if (route < 1 || validUntil.getYear() < 1900 || validUntil.getYear() > 9999
                    || amount.signum() < 0 || amount.scale() > 2 || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
                throw new IllegalArgumentException("Invalid route, date or amount");
            }
            this.id = id; this.registration = registration; this.name = name; this.route = route;
            this.stop = stop; this.validUntil = validUntil; this.amount = amount.setScale(2);
        }

        String encode() {
            return String.join("|", id, registration, escape(name), Integer.toString(route),
                    escape(stop), validUntil.toString(), amount.toPlainString());
        }

        private static String escape(String text) { return text.replace("\\","\\\\").replace("|","\\|"); }

        static Pass parse(String line) {
            List<String> fields = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean escaped = false;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (escaped) {
                    if (c != '|' && c != '\\') throw new IllegalArgumentException("Invalid escape");
                    field.append(c);
                    escaped = false;
                } else if (c == '\\') {
                    escaped = true;
                } else if (c == '|') {
                    fields.add(field.toString()); field.setLength(0);
                } else {
                    field.append(c);
                }
            }
            if (escaped) throw new IllegalArgumentException("Incomplete escape");
            fields.add(field.toString());
            if (fields.size() != 7) throw new IllegalArgumentException("Expected seven fields");
            return new Pass(fields.get(0), fields.get(1), fields.get(2), Integer.parseInt(fields.get(3)),
                    fields.get(4), LocalDate.parse(fields.get(5)), new BigDecimal(fields.get(6)));
        }
    }

    private final Path file;
    private final Consumer<String> diagnostics;

    FullPassStore(Path file, Consumer<String> diagnostics) {
        this.file = file; this.diagnostics = diagnostics;
    }

    synchronized void add(Pass pass) throws IOException {
        if (find(pass.id) != null) throw new IllegalArgumentException("Duplicate pass ID");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            writer.write(pass.encode());
            writer.write('\n');
        }
    }

    Pass find(String id) throws IOException {
        return scan(pass -> pass.id.equals(id));
    }

    List<Pass> route(int route) throws IOException {
        List<Pass> matches = new ArrayList<>();
        scan(pass -> {
            if (pass.route == route) matches.add(pass);
            return false;
        });
        return matches;
    }

    private Pass scan(java.util.function.Predicate<Pass> until) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            int number = 0;
            for (String line; (line = reader.readLine()) != null;) {
                number++;
                Pass pass;
                try { pass = Pass.parse(line); }
                catch (IllegalArgumentException | java.time.DateTimeException e) {
                    diagnostics.accept("Line " + number + ": " + e.getMessage());
                    continue;
                }
                if (until.test(pass)) return pass;
            }
        } catch (NoSuchFileException missing) {
            return null;
        }
        return null;
    }

    static Pass sample(int n) {
        return new Pass("P" + n, Integer.toString(192300000 + n), "Asha|Rao\\Sample", 7,
                "North Gate", LocalDate.of(2027, 3, 31), new BigDecimal("3800.00"));
    }

    public static void main(String[] args) throws IOException {
        Path root = Files.createTempDirectory("pass-test");
        Path file = root.resolve("passes.txt");
        try {
            FullPassStore store = new FullPassStore(file, System.out::println);
            if (store.find("P1") != null || !store.route(7).isEmpty()) throw new AssertionError("Missing file");
            store.add(sample(1));
            Files.write(file, Arrays.asList("BAD|ROW", "P34|192300034|Demo|7|Gate|2027-03-31|abc", ""),
                    StandardCharsets.UTF_8, StandardOpenOption.APPEND);
            store.add(sample(2));
            Pass found = store.find("P2");
            if (found == null || !found.name.equals("Asha|Rao\\Sample") || store.find("P34") != null
                    || store.find("Missing") != null || store.route(7).size() != 2) throw new AssertionError();
            try { store.add(sample(2)); throw new AssertionError("Duplicate allowed"); }
            catch (IllegalArgumentException expected) { System.out.println(expected.getMessage()); }
            System.out.println("Escaped name preserved. Missing IDs absent and two route records returned.");
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(root); }
    }
}
