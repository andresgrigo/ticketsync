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
 * FXML controller for the event create/edit dialog ({@code EventFormView.fxml}).
 *
 * <p>Operates in two modes controlled by {@link Mode}:
 * <ul>
 *   <li>{@link Mode#CREATE} — all fields empty and editable; validates name,
 *       date, optional time format, future date-time, and venue.</li>
 *   <li>{@link Mode#EDIT} — all fields pre-populated from the existing event;
 *       all fields are editable including the event name. The original event's
 *       {@code eventId}, {@code createdBy}, and {@code createdAt} are preserved
 *       in the returned event from {@link #getEventFromForm()}.</li>
 * </ul>
 *
 * <p>The dialog container (owned by {@code AdminDashboardController}) attaches
 * an event filter to the OK button that calls {@link #validate()} before
 * allowing the dialog to close, keeping all validation logic here.
 */
public class EventFormController {

    /** Creates a new {@code EventFormController} instance (invoked by FXMLLoader via reflection). */
    public EventFormController() {
    }

    /** Operational mode that determines pre-population behaviour. */
    public enum Mode {
        /** All fields empty; a new event will be constructed from form values. */
        CREATE,
        /** All fields pre-populated from an existing event; {@code eventId} is preserved. */
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
     * Sets the operational mode for this form.
     *
     * <p>In both {@link Mode#CREATE} and {@link Mode#EDIT} all fields are
     * visible and editable. This method exists so the controller knows
     * whether to preserve the original event's identity fields when
     * {@link #getEventFromForm()} is called.
     *
     * @param mode the mode to apply; must not be {@code null}
     */
    public void setMode(Mode mode) {
        this.mode = mode;
    }

    /**
     * Pre-populates the form fields with data from an existing event.
     *
     * <p>Should be called in {@link Mode#EDIT} after {@link #setMode(Mode)}.
     * Saves the original event reference so that {@link #getEventFromForm()}
     * can preserve identity fields ({@code eventId}, {@code createdBy},
     * {@code createdAt}).
     *
     * @param event the event whose data should be displayed; must not be {@code null}
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
     * Validates the form contents.
     *
     * <p>Validation rules applied in order:
     * <ol>
     *   <li>Name must not be blank.</li>
     *   <li>Date must be selected.</li>
     *   <li>Time, if non-blank, must match {@code \d{2}:\d{2}}.</li>
     *   <li>The combined date-time (defaulting to midnight when time is blank)
     *       must be in the future.</li>
     *   <li>Venue must not be blank.</li>
     * </ol>
     *
     * <p>On the first failure the error label is made visible with an
     * explanatory message and {@code false} is returned. On success the
     * error label is hidden and {@code true} is returned.
     *
     * @return {@code true} if all validations pass; {@code false} otherwise
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
     * Constructs and returns an {@link Event} from the current form values.
     *
     * <p>In {@link Mode#EDIT} the returned event preserves the original
     * event's {@code eventId}, {@code createdBy}, and {@code createdAt}
     * fields so that {@code EventService.updateEvent()} can identify and
     * correctly update the record.
     *
     * <p>In {@link Mode#CREATE} the {@code eventId} is {@code 0},
     * {@code createdBy} is {@code 0}, and {@code createdAt} is {@code null};
     * the service layer assigns real values on insert.
     *
     * @return the populated {@link Event}; never {@code null}
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
     * Displays a validation error message inside the dialog.
     *
     * @param message the error message to display; must not be {@code null}
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
