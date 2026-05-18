package com.mby.myStore.Services;

import com.mby.myStore.Exceptions.RecordNotFoundException;
import com.mby.myStore.Model.Employee;
import com.mby.myStore.Repositories.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

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
 * Clase de pruebas unitarias para el servicio EmployeeService.
 * Mediante el uso de Mockito, se aísla la lógica de negocio de la gestión de la plantilla de barberos.
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private Employee empleadoBase;
    private Employee datosActualizados;

    /**
     * Configuración del entorno antes de cada test.
     * Prepara entidades reutilizables que representan barberos de la plantilla.
     */
    @BeforeEach
    void setUp() {
        empleadoBase = new Employee();
        empleadoBase.setId(1L);
        empleadoBase.setName("Carlos Barber");
        empleadoBase.setActive(true);
        empleadoBase.setHireDate(LocalDate.now().minusMonths(6));

        datosActualizados = new Employee();
        datosActualizados.setName("Carlos Actualizado");
        datosActualizados.setActive(false);
        datosActualizados.setHireDate(LocalDate.now());
    }

    // =========================================================================
    // TESTS PARA GETALL
    // =========================================================================

    /**
     * Verifica que si existen empleados registrados en el sistema,
     * el método getAll devuelva el listado completo correctamente.
     */
    @Test
    void getAll_DebeDevolverListaDeEmpleados_CuandoExistenRegistros() {
        // Arrange
        when(employeeRepository.findAll()).thenReturn(List.of(empleadoBase));

        // Act
        List<Employee> resultado = employeeService.getAll();

        // Assert
        assertThat(resultado).isNotEmpty().hasSize(1);
        assertThat(resultado.get(0).getName()).isEqualTo("Carlos Barber");
        verify(employeeRepository, times(2)).findAll(); // Llama dos veces en tu lógica: una para el isEmpty() y otra para el return
    }

    /**
     * Comprueba la robustez del método getAll. Si la base de datos no contiene
     * ningún barbero, debe retornar una instancia de lista vacía en lugar de un valor nulo.
     */
    @Test
    void getAll_DebeDevolverListaVacia_CuandoNoHayRegistros() {
        // Arrange
        when(employeeRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<Employee> resultado = employeeService.getAll();

        // Assert
        assertThat(resultado).isNotNull().isEmpty();
        verify(employeeRepository, times(1)).findAll(); // Solo entra al primer condicional
    }

    // =========================================================================
    // TESTS PARA GETEMPLOYEEBYID
    // =========================================================================

    /**
     * Test de camino feliz para la búsqueda por ID.
     * Comprueba que si el barbero existe, se devuelva la instancia con todos sus campos íntegros.
     */
    @Test
    void getEmployeeById_DebeDevolverEmpleado_CuandoIdExiste() {
        // Arrange
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(empleadoBase));

        // Act
        Employee resultado = employeeService.getEmployeeById(1L);

        // Assert
        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L);
        assertThat(resultado.getName()).isEqualTo("Carlos Barber");
    }

    /**
     * Verifica que si se consulta un identificador inexistente, el sistema salte
     * de forma controlada lanzando la excepción RecordNotFoundException.
     */
    @Test
    void getEmployeeById_DebeLanzarException_CuandoIdNoExiste() {
        // Arrange
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> employeeService.getEmployeeById(99L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("No existe el empleado con ID: 99");
    }

    // =========================================================================
    // TESTS PARA ADDEMPLOYEE
    // =========================================================================

    /**
     * Comprueba que el alta de un nuevo empleado se procese delegando
     * correctamente la persistencia física en el repositorio.
     */
    @Test
    void addEmployee_DebeGuardarEmpleadoCorrectamente() {
        // Arrange
        when(employeeRepository.save(any(Employee.class))).thenReturn(empleadoBase);

        // Act
        employeeService.addEmployee(empleadoBase);

        // Assert
        verify(employeeRepository, times(1)).save(empleadoBase);
    }

    // =========================================================================
    // TESTS PARA DELETEEMPLOYEE
    // =========================================================================

    /**
     * Verifica que si un barbero consta en el sistema, el método de eliminación
     * invoque de manera efectiva la instrucción de borrado en la base de datos.
     */
    @Test
    void deleteEmployee_DebeEliminar_CuandoEmpleadoExiste() {
        // Arrange
        when(employeeRepository.existsById(1L)).thenReturn(true);

        // Act
        employeeService.deleteEmployee(1L);

        // Assert
        verify(employeeRepository, times(1)).deleteById(1L);
    }

    /**
     * Protege el borrado frente a llamadas huérfanas. Si el barbero no existe,
     * debe denegar la operación lanzando un RecordNotFoundException.
     */
    @Test
    void deleteEmployee_DebeLanzarException_CuandoEmpleadoNoExiste() {
        // Arrange
        when(employeeRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> employeeService.deleteEmployee(99L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("No se puede eliminar: empleado no encontrado");

        verify(employeeRepository, never()).deleteById(any());
    }

    // =========================================================================
    // TESTS PARA UPDATEEMPLOYEE
    // =========================================================================

    /**
     * Valida el proceso de actualización del perfil del profesional.
     * Comprueba que el sistema recupere el registro original, sobrescriba únicamente
     * los campos editables permitidos y guarde los cambios en el almacén de datos.
     */
    @Test
    void updateEmployee_DebeActualizarCamposPermitidos_CuandoIdExiste() {
        // Arrange
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(empleadoBase));
        when(employeeRepository.save(any(Employee.class))).thenReturn(empleadoBase);

        // Act
        Employee resultado = employeeService.updateEmployee(1L, datosActualizados);

        // Assert
        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L); // El ID no debe variar
        assertThat(resultado.getName()).isEqualTo("Carlos Actualizado"); // Nombre modificado
        assertThat(resultado.getActive()).isFalse(); // Estado modificado
        assertThat(resultado.getHireDate()).isEqualTo(datosActualizados.getHireDate()); // Fecha modificada
        verify(employeeRepository, times(1)).save(empleadoBase);
    }

    // =========================================================================
    // TESTS PARA GETACTIVEEMPLOYEESCOUNT
    // =========================================================================

    /**
     * Evalúa la métrica del cuadro de mandos del administrador.
     * Verifica que el servicio retorne con precisión la cantidad agregada de barberos en activo.
     */
    @Test
    void getActiveEmployeesCount_DebeRetornarElNumeroDeBarberosActivos() {
        // Arrange
        when(employeeRepository.countByActiveTrue()).thenReturn(5L);

        // Act
        Long contador = employeeService.getActiveEmployeesCount();

        // Assert
        assertThat(contador).isEqualTo(5L);
        verify(employeeRepository, times(1)).countByActiveTrue();
    }
}