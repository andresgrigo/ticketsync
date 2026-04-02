module com.ticketsync {
    requires javafx.controls;
    requires javafx.fxml;
    requires transitive java.sql;
    requires com.zaxxer.hikari;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires jbcrypt;
    requires atlantafx.base;
    requires jasypt;

    opens com.ticketsync to javafx.fxml;
    opens com.ticketsync.util;
    opens com.ticketsync.controller to javafx.fxml;
    exports com.ticketsync;
    exports com.ticketsync.util;
    exports com.ticketsync.model;
    exports com.ticketsync.dao;
    exports com.ticketsync.service;
    exports com.ticketsync.controller;
    exports com.ticketsync.viewmodel;
}
