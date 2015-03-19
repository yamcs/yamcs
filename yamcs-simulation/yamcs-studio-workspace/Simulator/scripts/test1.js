public class JDBCSampleService extends JDBCService {

    public JDBCSampleService() {
        super(new JDBCServiceDescription("jdbcSample", "A test service")
                .dataSource(new SimpleDataSource("jdbc:mysql://localhost/archive?user=root&password=hkumar"))
                .executorService(Executors.newSingleThreadExecutor(org.epics.pvmanager.util.Executors.namedPool("jdbcSample")))
                .addServiceMethod(new JDBCServiceMethodDescription("query", "A test query")
                    .query("SELECT * FROM Data")
                    .queryResult("result", "The query result")
                )
                .addServiceMethod(new JDBCServiceMethodDescription("insert", "A test insertquery")
                    .query("INSERT INTO `test`.`Data` (`Name`, `Index`, `Value`) VALUES (?, ?, ?)")
                    .addArgument("name", "The name", VString.class)
                    .addArgument("index", "The index", VNumber.class)
                    .addArgument("value", "The value", VNumber.class)
                ));
    }

}
