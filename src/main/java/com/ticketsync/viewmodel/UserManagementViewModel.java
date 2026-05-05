package com.ticketsync.viewmodel;

import com.ticketsync.model.User;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;

/**
 * Estado de la capa de presentación para la tabla de gestión de usuarios del Panel de Administración.
 *
 * <p>Mantiene una {@link ObservableList} de objetos {@link User} que el
 * {@code AdminDashboardController} enlaza a su {@code TableView}. Los cambios
 * en la lista se reflejan automáticamente en la UI mediante los enlaces de
 * propiedades de JavaFX.
 *
 * <p>Esta clase no tiene referencia a controles de la UI de JavaFX y por tanto puede ser
 * probada sin inicializar el toolkit de JavaFX.
 */
public class UserManagementViewModel {

    /** Crea un nuevo UserManagementViewModel con una lista de usuarios vacía. */
    public UserManagementViewModel() { }

    private final ObservableList<User> users = FXCollections.observableArrayList();
    private final BooleanProperty loading = new SimpleBooleanProperty(false);
    private final StringProperty statusMessage = new SimpleStringProperty("");
    private final ObjectProperty<User> selectedUser = new SimpleObjectProperty<>(null);

    /**
     * Retorna la lista observable de usuarios mostrados en la tabla.
     *
     * @return la lista observable de usuarios; nunca {@code null}
     */
    public ObservableList<User> usersProperty() {
        return users;
    }

    /**
     * Retorna la propiedad indicadora de carga.
     *
     * <p>Cuando es {@code true}, una operación de carga de datos en segundo plano está en progreso.
     *
     * @return la propiedad loading
     */
    public BooleanProperty loadingProperty() {
        return loading;
    }

    /**
     * Retorna la propiedad de mensaje de estado utilizada para mostrar texto de carga o error.
     *
     * @return la propiedad statusMessage
     */
    public StringProperty statusMessageProperty() {
        return statusMessage;
    }

    /**
     * Retorna la propiedad del usuario actualmente seleccionado.
     *
     * <p>Enlazada al modelo de selección del {@code TableView} para que los manejadores
     * de acciones del controlador puedan recuperar la fila seleccionada sin consultar
     * la tabla directamente.
     *
     * @return la propiedad selectedUser
     */
    public ObjectProperty<User> selectedUserProperty() {
        return selectedUser;
    }

    /**
     * Reemplaza el contenido de la lista de usuarios con la lista proporcionada.
     *
     * <p>La lista existente se limpia primero para que los datos previamente cargados
     * sean descartados antes de repoblar.
     *
     * @param list la nueva lista de usuarios a mostrar; no debe ser {@code null}
     */
    public void setUsers(List<User> list) {
        users.clear();
        users.addAll(list);
    }

    /**
     * Agrega un único usuario al final de la lista observable.
     *
     * @param user el usuario a agregar; no debe ser {@code null}
     */
    public void addUser(User user) {
        users.add(user);
    }

    /**
     * Elimina el usuario cuyo {@code userId} coincide con el usuario proporcionado.
     *
     * @param user el usuario a eliminar; igualado por {@code userId}
     */
    public void removeUser(User user) {
        users.removeIf(u -> u.getUserId() == user.getUserId());
    }

    /**
     * Reemplaza la entrada de la lista cuyo {@code userId} coincide con el del
     * usuario proporcionado con el nuevo objeto de usuario.
     *
     * @param updated el usuario actualizado; igualado y reemplazado por {@code userId}
     */
    public void updateUser(User updated) {
        for (int i = 0; i < users.size(); i++) {
            if (users.get(i).getUserId() == updated.getUserId()) {
                users.set(i, updated);
                return;
            }
        }
    }
}
