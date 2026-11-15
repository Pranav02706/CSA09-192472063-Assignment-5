import java.sql.*;
import java.util.*;

interface Dao<T, K> {
    void insert(T item) throws SQLException;
    Optional<T> findById(K key) throws SQLException;
    List<T> findAll() throws SQLException;
    boolean update(T item) throws SQLException;
    boolean delete(K key) throws SQLException;
}

final class Student {
    final String registration;
    final String name;
    Student(String registration, String name) {
        this.registration = Objects.requireNonNull(registration);
        this.name = Objects.requireNonNull(name);
    }
}

public final class StudentDao implements Dao<Student, String> {
    private final String url, user, password;

    StudentDao(String url, String user, String password) {
        this.url = url; this.user = user; this.password = password;
    }

    private Connection open() throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    private Student map(ResultSet rs) throws SQLException {
        return new Student(rs.getString("reg_no"), rs.getString("name"));
    }

    @Override public void insert(Student student) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("INSERT INTO student(reg_no,name) VALUES (?,?)")) {
            ps.setString(1, student.registration); ps.setString(2, student.name);
            ps.executeUpdate();
        }
    }

    @Override public Optional<Student> findById(String key) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("SELECT reg_no,name FROM student WHERE reg_no=?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? Optional.of(map(rs)) : Optional.empty(); }
        }
    }

    @Override public List<Student> findAll() throws SQLException {
        List<Student> list = new ArrayList<>();
        try (Connection c = open();
                PreparedStatement ps = c.prepareStatement("SELECT reg_no,name FROM student ORDER BY reg_no");
                ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(map(rs));
        }
        return list;
    }

    @Override public boolean update(Student student) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("UPDATE student SET name=? WHERE reg_no=?")) {
            ps.setString(1, student.name); ps.setString(2, student.registration);
            return ps.executeUpdate() == 1;
        }
    }

    @Override public boolean delete(String key) throws SQLException {
        try (Connection c = open(); PreparedStatement ps = c.prepareStatement("DELETE FROM student WHERE reg_no=?")) {
            ps.setString(1, key);
            return ps.executeUpdate() == 1;
        }
    }
}
