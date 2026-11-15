import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

public final class FileBench {
    private static final int MIB = 1024 * 1024;

    private static long[] scan(Path file, boolean buffered) throws IOException {
        long lines = 0, calls = 0;
        int last = -1;
        try (FileInputStream input = new FileInputStream(file.toFile())) {
            if (buffered) {
                byte[] buffer = new byte[8192];
                for (int n; (n = input.read(buffer)) != -1;) {
                    calls++;
                    for (int i = 0; i < n; i++) if (buffer[i] == '\n') lines++;
                    if (n > 0) last = buffer[n - 1] & 255;
                }
            } else {
                for (int c; (c = input.read()) != -1;) {
                    calls++;
                    if (c == '\n') lines++;
                    last = c;
                }
            }
        }
        if (last != -1 && last != '\n') lines++;
        return new long[]{lines, calls + 1};
    }

    private static void count(Path path, int[] sizes) throws IOException {
        byte[] data = new byte[MIB];
        Arrays.fill(data, (byte) 'x');
        for (int i = 127; i < data.length; i += 128) data[i] = '\n';
        Files.write(path, new byte[0]);
        if (scan(path, true)[0] != 0 || scan(path, false)[0] != 0) throw new AssertionError("empty");
        Files.write(path, new byte[]{'a','\n','b'});
        if (scan(path, true)[0] != 2 || scan(path, false)[0] != 2) throw new AssertionError("unterminated");
        Files.write(path, data);
        scan(path, true);
        scan(path, false);
        System.out.println("mib,method,repetition,lines,read_calls,ns");
        for (int mib : sizes) {
            try (OutputStream output = Files.newOutputStream(path)) {
                for (int n = 0; n < mib; n++) output.write(data);
            }
            for (int run = 1; run <= 3; run++) {
                for (int offset = 0; offset < 2; offset++) {
                    boolean buffered = ((run + offset) & 1) == 0;
                    long start = System.nanoTime();
                    long[] result = scan(path, buffered);
                    long elapsed = System.nanoTime() - start;
                    if (result[0] != (long) mib * (MIB / 128)) throw new AssertionError();
                    System.out.printf("%d,%s,%d,%d,%d,%d%n", mib, buffered ? "buffered" : "single",
                            run, result[0], result[1], elapsed);
                }
            }
        }
    }

    private static void lookup(Path path) throws IOException {
        System.out.println("records,repetition,target,ns");
        for (int n : new int[]{1000, 10000, 100000}) {
            try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                for (int i = 1; i <= n; i++) {
                    writer.write(FullPassStore.sample(i).encode());
                    writer.newLine();
                }
            }
            FullPassStore store = new FullPassStore(path, error -> { throw new AssertionError(error); });
            for (int run = -2; run < 5; run++) {
                for (String target : new String[]{"P1", "P" + n, "MISSING"}) {
                    long start = System.nanoTime();
                    FullPassStore.Pass pass = store.find(target);
                    long elapsed = System.nanoTime() - start;
                    if (target.equals("MISSING") ? pass != null : pass == null || !pass.id.equals(target)) {
                        throw new AssertionError(target);
                    }
                    if (run >= 0) System.out.printf("%d,%d,%s,%d%n", n, run + 1, target, elapsed);
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path path = Files.createTempFile("file-benchmark", ".txt");
        try {
            if (args.length > 0 && args[0].equals("lines")) {
                if (args.length == 2 && args[1].equals("smoke")) count(path, new int[]{1});
                else if (args.length == 1) count(path, new int[]{1, 10, 50});
                else throw new IllegalArgumentException("Use lines or lines smoke");
            }
            else lookup(path);
        } finally { Files.deleteIfExists(path); }
    }
}
