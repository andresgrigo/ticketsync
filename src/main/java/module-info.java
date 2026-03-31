module com.ticketsync {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.sql;

    opens com.ticketsync to javafx.fxml;
    exports com.ticketsync;
    exports com.ticketsync.util;
}
