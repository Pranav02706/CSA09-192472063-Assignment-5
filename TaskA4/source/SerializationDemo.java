import java.io.*;
import java.sql.Connection;
import java.util.Collections;
import java.util.List;

public final class SerializationDemo {
    private static final class Hostel implements Serializable {
        private static final long serialVersionUID = 1L;
        final List<Room> rooms = Collections.singletonList(new Room());
    }
    private static final class Room implements Serializable {
        private static final long serialVersionUID = 1L;
        final List<Resident> residents = Collections.singletonList(new Resident());
    }
    private static final class Resident implements Serializable {
        private static final long serialVersionUID = 1L;
        String name = "Test Resident";
        String registration = "DEMO01";
        String phone;
        transient String password = "fixture-only";
        transient Connection connection;
    }

    public static void main(String[] args) throws Exception {
        ByteArrayOutputStream encoded = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(encoded)) {
            out.writeObject(new Hostel());
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(encoded.toByteArray()))) {
            Resident read = ((Hostel) in.readObject()).rooms.get(0).residents.get(0);
            if (!read.registration.equals("DEMO01") || read.password != null || read.connection != null) {
                throw new AssertionError("Round trip or transient fields incorrect");
            }
            System.out.println("Restored " + read.registration + "; password and connection omitted");
        }
    }
}
