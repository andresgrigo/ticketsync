package com.ticketsync.controller;

import com.ticketsync.model.Event;
import javafx.fxml.FXML;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Controlador FXML para el diálogo de creación/edición de eventos ({@code EventFormView.fxml}).
 *
 * <p>Opera en dos modos controlados por {@link Mode}:
 * <ul>
 *   <li>{@link Mode#CREATE} — todos los campos vacíos y editables; valida nombre,
 *       fecha, formato de hora opcional, fecha-hora futura y lugar.</li>
 *   <li>{@link Mode#EDIT} — todos los campos pre-rellenos desde el evento existente;
 *       todos los campos son editables incluyendo el nombre del evento. El
 *       {@code eventId}, {@code createdBy} y {@code createdAt} originales del evento se preservan
 *       en el evento retornado por {@link #getEventFromForm()}.</li>
 * </ul>
 *
 * <p>El contenedor del diálogo (propiedad de {@code AdminDashboardController}) adjunta
 * un filtro de evento al botón OK que llama a {@link #validate()} antes de
 * permitir que el diálogo se cierre, manteniendo toda la lógica de validación aquí.
 */
public class EventFormController {

    /** Crea una nueva instancia de {@code EventFormController} (invocada por FXMLLoader mediante reflexión). */
    public EventFormController() {
    }

    /** Modo operacional que determina el comportamiento de pre-relleno. */
    public enum Mode {
        /** Todos los campos vacíos; se construirá un nuevo evento a partir de los valores del formulario. */
        CREATE,
        /** Todos los campos pre-rellenos desde un evento existente; {@code eventId} se preserva. */
        EDIT
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    @FXML
    private TextField nameField;

    @FXML
    private DatePicker eventDatePicker;

    @FXML
    private TextField timeField;

    @FXML
    private TextField venueField;

    @FXML
    private TextArea descriptionArea;

    @FXML
    private Label errorLabel;

    private Mode mode = Mode.CREATE;
    private Event originalEvent = null;

    /**
     * Establece el modo operacional para este formulario.
     *
     * <p>Tanto en {@link Mode#CREATE} como en {@link Mode#EDIT} todos los campos son
     * visibles y editables. Este método existe para que el controlador sepa
     * si debe preservar los campos de identidad del evento original cuando
     * se llame a {@link #getEventFromForm()}.
     *
     * @param mode el modo a aplicar; no debe ser {@code null}
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Pre-rellena los campos del formulario con datos de un evento existente.
     *
     * <p>Debe llamarse en {@link Mode#EDIT} después de {@link #setMode(Mode)}.
     * Guarda la referencia al evento original para que {@link #getEventFromForm()}
     * pueda preservar los campos de identidad ({@code eventId}, {@code createdBy},
     * {@code createdAt}).
     *
     * @param event el evento cuyos datos deben mostrarse; no debe ser {@code null}
     */
    public void setEvent(Event event) {
        this.originalEvent = event;
        nameField.setText(event.getName() != null ? event.getName() : "");
        if (event.getEventDate() != null) {
            eventDatePicker.setValue(event.getEventDate().toLocalDate());
            LocalTime t = event.getEventDate().toLocalTime();
            timeField.setText(t.equals(LocalTime.MIDNIGHT) ? "" : t.format(TIME_FMT));
        } else {
            eventDatePicker.setValue(null);
            timeField.setText("");
        }
        venueField.setText(event.getVenue() != null ? event.getVenue() : "");
        descriptionArea.setText(event.getDescription() != null ? event.getDescription() : "");
    }

    /**
     * Valida el contenido del formulario.
     *
     * <p>Reglas de validación aplicadas en orden:
     * <ol>
     *   <li>El nombre no debe estar en blanco.</li>
     *   <li>La fecha debe estar seleccionada.</li>
     *   <li>La hora, si no está en blanco, debe coincidir con {@code \d{2}:\d{2}}.</li>
     *   <li>La fecha-hora combinada (por defecto a medianoche cuando la hora está en blanco)
     *       debe ser en el futuro.</li>
     *   <li>El lugar no debe estar en blanco.</li>
     * </ol>
     *
     * <p>En la primera falla la etiqueta de error se hace visible con un
     * mensaje explicativo y se retorna {@code false}. En caso de éxito la
     * etiqueta de error se oculta y se retorna {@code true}.
     *
     * @return {@code true} si todas las validaciones pasan; {@code false} en caso contrario
     */
    public boolean validate() {
        if (nameField.getText() == null || nameField.getText().isBlank()) {
            showError("Name is required");
            return false;
        }
        LocalDate date = eventDatePicker.getValue();
        if (date == null) {
            showError("Event date is required");
            return false;
        }
        String timeStr = timeField.getText() == null ? "" : timeField.getText().trim();
        if (!timeStr.isEmpty() && !timeStr.matches("^\\d{2}:\\d{2}$")) {
            showError("Time must be in HH:mm format");
            return false;
        }
        LocalTime time;
        try {
            time = timeStr.isEmpty() ? LocalTime.MIDNIGHT : LocalTime.parse(timeStr, TIME_FMT);
        } catch (DateTimeParseException e) {
            showError("Time must be in HH:mm format");
            return false;
        }
        LocalDateTime eventDateTime = LocalDateTime.of(date, time);
        if (!eventDateTime.isAfter(LocalDateTime.now())) {
            showError("Event date must be in the future");
            return false;
        }
        if (venueField.getText() == null || venueField.getText().isBlank()) {
            showError("Venue is required");
            return false;
        }
        hideError();
        return true;
    }

    /**
     * Construye y retorna un {@link Event} a partir de los valores actuales del formulario.
     *
     * <p>En {@link Mode#EDIT} el evento retornado preserva el {@code eventId},
     * {@code createdBy} y {@code createdAt} del evento original para que
     * {@code EventService.updateEvent()} pueda identificar y actualizar correctamente el registro.
     *
     * <p>En {@link Mode#CREATE} el {@code eventId} es {@code 0},
     * {@code createdBy} es {@code 0} y {@code createdAt} es {@code null};
     * la capa de servicio asigna valores reales al insertar.
     *
     * @return el {@link Event} poblado; nunca {@code null}
     */
    public Event getEventFromForm() {
        String name = nameField.getText().trim();
        LocalDate date = eventDatePicker.getValue();
        String timeStr = timeField.getText() == null ? "" : timeField.getText().trim();
        LocalTime time = timeStr.isEmpty() ? LocalTime.MIDNIGHT : LocalTime.parse(timeStr, TIME_FMT);
        LocalDateTime eventDateTime = date != null ? LocalDateTime.of(date, time) : null;
        String venue = venueField.getText().trim();
        String description = descriptionArea.getText() == null ? "" : descriptionArea.getText().trim();

        if (mode == Mode.EDIT && originalEvent != null) {
            Event updated = new Event(
                    originalEvent.getEventId(),
                    name,
                    eventDateTime,
                    venue,
                    description,
                    originalEvent.isActive(),
                    originalEvent.getCreatedBy(),
                    originalEvent.getCreatedAt()
            );
            return updated;
        }

        Event newEvent = new Event();
        newEvent.setName(name);
        newEvent.setEventDate(eventDateTime);
        newEvent.setVenue(venue);
        newEvent.setDescription(description);
        newEvent.setActive(true);
        return newEvent;
    }

    /**
     * Muestra un mensaje de error de validación dentro del diálogo.
     *
     * @param message el mensaje de error a mostrar; no debe ser {@code null}
     */
    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setOpacity(1.0);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void hideError() {
        errorLabel.setText("");
        errorLabel.setOpacity(0.0);
    }
}
