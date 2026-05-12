/**
 * Módulo principal de la aplicación TicketSync para punto de venta.
 *
 * <p>Declara todas las dependencias del módulo y controla qué paquetes
 * se exportan o abren al entorno de ejecución de JavaFX para reflexión.
 */
module com.ticketsync {
    requires transitive javafx.controls;
    requires javafx.fxml;
    requires java.desktop;
    requires transitive javafx.base;
    requires transitive javafx.graphics;
    requires transitive java.sql;
    requires com.zaxxer.hikari;
    requires org.postgresql.jdbc;
    requires flyway.core;
    requires com.fasterxml.jackson.databind;
    requires org.apache.logging.log4j;
    requires org.apache.logging.log4j.core;
    requires jbcrypt;
    requires atlantafx.base;
    requires org.apache.pdfbox;

    opens com.ticketsync to javafx.fxml;
    opens com.ticketsync.util;
    opens com.ticketsync.controller to javafx.fxml;
    opens com.ticketsync.viewmodel;
    opens db.migration;
    exports com.ticketsync;
    exports com.ticketsync.util;
    exports com.ticketsync.model;
    exports com.ticketsync.dao;
    exports com.ticketsync.service;
    exports com.ticketsync.exception;
    exports com.ticketsync.controller;
    exports com.ticketsync.viewmodel;
}
