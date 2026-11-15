public final class StudentDaoTest {
    public static void main(String[] args) throws Exception {
        StudentDao dao = new StudentDao(LocalDb.url(), LocalDb.user(), LocalDb.password());
        String id = "000090001";
        dao.delete(id);
        try {
            if (dao.findById(id).isPresent()) throw new AssertionError("Unexpected existing student");
            dao.insert(new Student(id, "Sample Student"));
            if (!dao.findById(id).get().name.equals("Sample Student")) throw new AssertionError("Insert");
            if (!dao.update(new Student(id, "Updated Student"))
                    || dao.findAll().stream().noneMatch(s -> s.registration.equals(id) && s.name.equals("Updated Student"))) {
                throw new AssertionError("Update/list");
            }
            if (!dao.delete(id) || dao.findById(id).isPresent()) throw new AssertionError("Delete");
            System.out.println("StudentDao MySQL insert, find, list, update and delete verified.");
        } finally { dao.delete(id); }
    }
}
