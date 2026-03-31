module com.ticketsync {
    requires javafx.controls;
    requires javafx.fxml;

    opens com.ticketsync to javafx.fxml;
    exports com.ticketsync;
}
