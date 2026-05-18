package com.mby.myStore.Services;

import com.mby.myStore.DTO.UserResponse;
import com.mby.myStore.Exceptions.DuplicateRecordException;
import com.mby.myStore.Exceptions.InvalidCredentialsException;
import com.mby.myStore.Exceptions.RecordNotFoundException;
import com.mby.myStore.Model.Role;
import com.mby.myStore.Model.User;
import com.mby.myStore.Repositories.UserRepository;
import com.mby.myStore.Utils.HashPsw;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Clase de pruebas unitarias para el servicio UserService.
 * Valida la lógica de negocio de gestión de clientes, autenticación y control de accesos.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User usuarioBase;
    private User nuevosDatos;
    private Role rolCliente;

    /**
     * Inicialización del entorno de pruebas.
     * Configura instancias de usuarios y roles antes de cada ejecución.
     */
    @BeforeEach
    void setUp() {
        rolCliente = Role.CUSTOMER; // Asumiendo que es un Enum o constante válida en tu modelo

        usuarioBase = new User();
        usuarioBase.setId(1L);
        usuarioBase.setName("Manuel Gómez");
        usuarioBase.setEmail("manuel@email.com");
        usuarioBase.setPassword("hashed_password_123");
        usuarioBase.setTelNumber("600123456");
        usuarioBase.setRegisterDate(Instant.now());
        usuarioBase.setRole(rolCliente);

        nuevosDatos = new User();
        nuevosDatos.setName("Manuel Editado");
        nuevosDatos.setEmail("nuevo_manuel@email.com");
        nuevosDatos.setPassword("new_secure_pass");
    }

    // =========================================================================
    // TESTS PARA GETALL
    // =========================================================================

    /**
     * Verifica que si existen usuarios registrados en el sistema,
     * el método getAll devuelva la lista mapeada correctamente a objetos DTO.
     */
    @Test
    void getAll_DebeDevolverListaDeUsuarios_CuandoExistenRegistros() {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of(usuarioBase));

        // Act
        List<UserResponse> resultado = userService.getAll();

        // Assert
        assertThat(resultado).isNotEmpty().hasSize(1);
        assertThat(resultado.get(0).getName()).isEqualTo("Manuel Gómez");
        assertThat(resultado.get(0).getEmail()).isEqualTo("manuel@email.com");
        verify(userRepository, times(1)).findAll();
    }

    /**
     * Asegura que si no consta ningún usuario en la base de datos,
     * el método responda devolviendo una lista vacía en lugar de nulo.
     */
    @Test
    void getAll_DebeDevolverListaVacia_CuandoNoHayRegistros() {
        // Arrange
        when(userRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<UserResponse> resultado = userService.getAll();

        // Assert
        assertThat(resultado).isNotNull().isEmpty();
        verify(userRepository, times(1)).findAll();
    }

    // =========================================================================
    // TESTS PARA GETUSERBYID
    // =========================================================================

    /**
     * Test de camino feliz para la búsqueda por identificador único.
     * Comprueba que se localice al usuario y se transforme de forma íntegra a UserResponse.
     */
    @Test
    void getUserById_DebeDevolverUsuarioDTO_CuandoIdExiste() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuarioBase));

        // Act
        UserResponse resultado = userService.getUserById(1L);

        // Assert
        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getName()).isEqualTo("Manuel Gómez");
    }

    /**
     * Valida que si se consulta un ID huérfano, el sistema salte con
     * la excepción controlada RecordNotFoundException.
     */
    @Test
    void getUserById_DebeLanzarException_CuandoIdNoExiste() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.getUserById(99L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("No existe un cliente con el id 99");
    }

    // =========================================================================
    // TESTS PARA ADDUSER
    // =========================================================================

    /**
     * Comprueba que el alta de un usuario se procese delegando su guardado
     * en el repositorio si el correo electrónico no está duplicado.
     */
    @Test
    void addUser_DebeGuardarUsuario_CuandoEmailEsUnico() {
        // Arrange
        when(userRepository.existsByEmail("manuel@email.com")).thenReturn(false);

        // Act
        userService.addUser(usuarioBase);

        // Assert
        verify(userRepository, times(1)).save(usuarioBase);
    }

    /**
     * Protege la integridad de la base de datos frente a duplicados.
     * Si el email ya está registrado, debe lanzar un DuplicateRecordException y abortar el guardado.
     */
    @Test
    void addUser_DebeLanzarException_CuandoEmailYaExiste() {
        // Arrange
        when(userRepository.existsByEmail("manuel@email.com")).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> userService.addUser(usuarioBase))
                .isInstanceOf(DuplicateRecordException.class)
                .hasMessageContaining("el email introducido ya existe");

        verify(userRepository, never()).save(any(User.class));
    }

    // =========================================================================
    // TESTS PARA UPDATEUSER
    // =========================================================================

    /**
     * Valida la actualización de perfiles de usuario.
     * Comprueba que tras verificar la existencia del ID, se modifiquen únicamente los campos
     * permitidos, manteniendo invariables otros datos (como el número de teléfono original).
     */
    @Test
    void updateUser_DebeActualizarCamposPermitidos_CuandoIdExiste() {
        // Arrange
        when(userRepository.existsById(1L)).thenReturn(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(usuarioBase));
        when(userRepository.save(any(User.class))).thenReturn(usuarioBase);

        // Act
        UserResponse response = userService.updateUser(nuevosDatos, 1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(usuarioBase.getName()).isEqualTo("Manuel Editado");
        assertThat(usuarioBase.getEmail()).isEqualTo("nuevo_manuel@email.com");
        assertThat(usuarioBase.getTelNumber()).isEqualTo("600123456"); // Mantiene el original según tu lógica
        verify(userRepository, times(1)).save(usuarioBase);
    }

    /**
     * Evita la modificación de datos de usuarios inexistentes arrojando RecordNotFoundException.
     */
    @Test
    void updateUser_DebeLanzarException_CuandoIdNoExiste() {
        // Arrange
        when(userRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.updateUser(nuevosDatos, 99L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("No existe un usuario con el id 99");

        verify(userRepository, never()).save(any(User.class));
    }

    // =========================================================================
    // TESTS PARA DELETEUSER
    // =========================================================================

    @Test
    void deleteUser_DebeEliminar_CuandoExiste() {
        // Arrange
        when(userRepository.existsById(1L)).thenReturn(true);

        // Act
        userService.deleteUser(1L);

        // Assert
        verify(userRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteUser_DebeLanzarException_CuandoNoExiste() {
        // Arrange
        when(userRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> userService.deleteUser(99L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("No existe un cliente con el id 99");

        verify(userRepository, never()).deleteById(any());
    }

    // =========================================================================
    // TESTS PARA GETUSERBYEMAIL
    // =========================================================================

    @Test
    void getUserByEmail_DebeRetornarEntidadUser_CuandoEmailExiste() {
        // Arrange
        when(userRepository.getByEmail("manuel@email.com")).thenReturn(Optional.of(usuarioBase));

        // Act
        User resultado = userService.getUserByEmail("manuel@email.com");

        // Assert
        assertThat(resultado).isNotNull();
        assertThat(resultado.getEmail()).isEqualTo("manuel@email.com");
    }

    // =========================================================================
    // TESTS PARA LOGIN (SISTEMA DE AUTENTICACIÓN)
    // =========================================================================

    /**
     * Test de camino feliz para el proceso de Login.
     * Utiliza MockedStatic para simular de forma controlada el validador de contraseñas HashPsw
     * y comprueba que unas credenciales válidas devuelvan el DTO de respuesta del perfil.
     */
    @Test
    void login_DebeAutenticarYDevolverDTO_CuandoCredencialesSonCorrectas() throws InvalidCredentialsException {
        // Arrange
        when(userRepository.getByEmail("manuel@email.com")).thenReturn(Optional.of(usuarioBase));

        // Abrimos un entorno simulado temporal para interceptar la clase estática HashPsw de tu utilidad
        try (MockedStatic<HashPsw> mockedHashPsw = mockStatic(HashPsw.class)) {
            mockedHashPsw.when(() -> HashPsw.checkPassword("password_plano", "hashed_password_123"))
                    .thenReturn(true);

            // Act
            UserResponse response = userService.login("manuel@email.com", "password_plano");

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getName()).isEqualTo("Manuel Gómez");
        }
    }

    /**
     * Verifica el rechazo de accesos fraudulentos al sistema.
     * Si la contraseña en texto plano no coincide con el Hash almacenado, arroja InvalidCredentialsException.
     */
    @Test
    void login_DebeLanzarException_CuandoContrasenaEsIncorrecta() {
        // Arrange
        when(userRepository.getByEmail("manuel@email.com")).thenReturn(Optional.of(usuarioBase));

        try (MockedStatic<HashPsw> mockedHashPsw = mockStatic(HashPsw.class)) {
            mockedHashPsw.when(() -> HashPsw.checkPassword("password_incorrecto", "hashed_password_123"))
                    .thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> userService.login("manuel@email.com", "password_incorrecto"))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("Contraseña incorrecta");
        }
    }

    // =========================================================================
    // TESTS PARA CONSULTAS DE BÚSQUEDA FLEXIBLE
    // =========================================================================

    @Test
    void getUsersByName_DebeDevolverFiltrado() {
        // Arrange
        when(userRepository.findByNameContainingIgnoreCase("manuel")).thenReturn(List.of(usuarioBase));

        // Act
        List<UserResponse> resultado = userService.getUsersByName("manuel");

        // Assert
        assertThat(resultado).isNotEmpty().hasSize(1);
        verify(userRepository, times(1)).findByNameContainingIgnoreCase("manuel");
    }

    @Test
    void getUsersByRol_DebeDevolverFiltradoPorRol() {
        // Arrange
        when(userRepository.findByRole(rolCliente)).thenReturn(List.of(usuarioBase));

        // Act
        List<UserResponse> resultado = userService.getUsersByRol(rolCliente);

        // Assert
        assertThat(resultado).isNotEmpty().hasSize(1);
        verify(userRepository, times(1)).findByRole(rolCliente);
    }
}