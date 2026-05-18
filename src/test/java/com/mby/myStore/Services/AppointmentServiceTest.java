package com.mby.myStore.Services;

import com.mby.myStore.DTO.AppointmentRequest;
import com.mby.myStore.DTO.AppointmentResponse;
import com.mby.myStore.Exceptions.DateNotValidException;
import com.mby.myStore.Exceptions.RecordNotFoundException;
import com.mby.myStore.Exceptions.SlotAlreadyOccupiedException;
import com.mby.myStore.Model.*;
import com.mby.myStore.Repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Clase de pruebas unitarias para el servicio AppointmentService.
 * Utiliza Mockito para aislar la lógica de negocio de la capa de acceso a datos.
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
class AppointmentServiceTest {

    @Mock
    private AppointmentRepository appointmentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ServiceRepository serviceRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private AbsenceRepository absenceRepository;
    @Mock
    private BusinessShiftRepository businessShiftRepository;

    @InjectMocks
    private AppointmentService appointmentService;

    private User cliente;
    private Employee empleado;
    private Service servicio;
    private AppointmentRequest requestValido;
    private LocalDate fechaLaborableFutura;

    /**
     * Configuración inicial antes de cada test.
     * Inicializa objetos ficticios (Mocks) y calcula un día laborable futuro
     * para evitar falsos negativos por las restricciones de fines de semana de la barbería.
     */
    @BeforeEach
    void setUp() {
        // Garantizar una fecha futura que sea de lunes a viernes para evitar excepciones de validación temporal
        fechaLaborableFutura = LocalDate.now().plusWeeks(1);
        while (fechaLaborableFutura.getDayOfWeek() == DayOfWeek.SATURDAY || fechaLaborableFutura.getDayOfWeek() == DayOfWeek.SUNDAY) {
            fechaLaborableFutura = fechaLaborableFutura.plusDays(1);
        }

        cliente = new User();
        cliente.setId(1L);
        cliente.setName("Manuel Gómez");
        cliente.setTelNumber("600123456");

        empleado = new Employee();
        empleado.setId(2L);
        empleado.setName("Barbero Carlos");

        servicio = new Service();
        servicio.setId(3L);
        servicio.setName("Corte + Barba");
        servicio.setDurationMinutes(45L);
        servicio.setPrice(20.0);

        requestValido = new AppointmentRequest();
        requestValido.setClientId(1L);
        requestValido.setEmployeeId(2L);
        requestValido.setServiceId(3L);
        requestValido.setDate(fechaLaborableFutura);
        requestValido.setStartTime(LocalTime.of(16, 0));
    }

    // =========================================================================
    // TESTS PARA CREATEAPPOINTMENT
    // =========================================================================

    /**
     * Test de camino feliz (Happy Path). Verifica que cuando todos los datos de entrada son correctos,
     * no hay bajas de empleados y el horario está libre, la cita se registra con estado CONFIRMED.
     */
    @Test
    void createAppointment_DebeRegistrarExitosamente_CuandoTodoEsValido() throws SlotAlreadyOccupiedException {
        // Arrange (Configurar el comportamiento de los repositorios simulados)
        when(userRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(empleado));
        when(serviceRepository.findById(3L)).thenReturn(Optional.of(servicio));
        when(absenceRepository.isEmployeeOnLeave(2L, fechaLaborableFutura)).thenReturn(false);
        when(appointmentRepository.checkAvailability(any(), any(), any(), any())).thenReturn(Collections.emptyList());

        // Act (Ejecutar la acción del servicio)
        AppointmentResponse response = appointmentService.createAppointment(requestValido);

        // Assert (Verificar resultados y mapeo DTO)
        assertThat(response).isNotNull();
        assertThat(response.getCustomerName()).isEqualTo("Manuel Gómez");
        assertThat(response.getEmployeeName()).isEqualTo("Barbero Carlos");
        assertThat(response.getServiceName()).isEqualTo("Corte + Barba");
        assertThat(response.getStatus()).isEqualTo(AppoStatus.CONFIRMED);
        verify(appointmentRepository, times(1)).save(any(Appointment.class));
    }

    /**
     * Verifica que el sistema salte con una excepción personalizada (RecordNotFoundException)
     * si se intenta agendar una cita para un cliente cuyo ID no existe en la base de datos.
     */
    @Test
    void createAppointment_DebeLanzarException_CuandoClienteNoExiste() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.createAppointment(requestValido))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("Cliente ID 1 no encontrado");
        verify(appointmentRepository, never()).save(any());
    }

    /**
     * Verifica el cumplimiento de la regla de negocio de ausencias. Si un barbero está de baja
     * o vacaciones el día solicitado, el sistema debe denegar la creación lanzando un DateNotValidException.
     */
    @Test
    void createAppointment_DebeLanzarException_CuandoEmpleadoEstaDeBaja() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(empleado));
        when(serviceRepository.findById(3L)).thenReturn(Optional.of(servicio));
        when(absenceRepository.isEmployeeOnLeave(2L, fechaLaborableFutura)).thenReturn(true);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.createAppointment(requestValido))
                .isInstanceOf(DateNotValidException.class)
                .hasMessageContaining("El profesional no está disponible");
    }

    /**
     * Comprueba las restricciones temporales del backend. Si el cliente envía una fecha
     * del pasado, el sistema debe rechazar la reserva de inmediato.
     */
    @Test
    void createAppointment_DebeLanzarException_CuandoFechaEsPasada() {
        // Arrange
        requestValido.setDate(LocalDate.now().minusDays(1));
        when(userRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(empleado));
        when(serviceRepository.findById(3L)).thenReturn(Optional.of(servicio));
        when(absenceRepository.isEmployeeOnLeave(2L, requestValido.getDate())).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.createAppointment(requestValido))
                .isInstanceOf(DateNotValidException.class)
                .hasMessageContaining("No se pueden programar citas en fechas pasadas.");
    }

    /**
     * Valida la política comercial del establecimiento: el negocio cierra sábados y domingos.
     * Si se solicita cita en fin de semana, debe lanzar una excepción semántica clara.
     */
    @Test
    void createAppointment_DebeLanzarException_CuandoEsFinDeSemana() {
        // Arrange
        LocalDate proximoSabado = LocalDate.now().plusWeeks(1);
        while (proximoSabado.getDayOfWeek() != DayOfWeek.SATURDAY) {
            proximoSabado = proximoSabado.plusDays(1);
        }
        requestValido.setDate(proximoSabado);

        when(userRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(empleado));
        when(serviceRepository.findById(3L)).thenReturn(Optional.of(servicio));
        when(absenceRepository.isEmployeeOnLeave(2L, proximoSabado)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.createAppointment(requestValido))
                .isInstanceOf(DateNotValidException.class)
                .hasMessageContaining("No abrimos los fines de semana");
    }

    /**
     * Comprueba la validación de solapamiento de la agenda. Si otra cita coincide temporalmente
     * con la que el usuario solicita, el sistema aborta la operación con un SlotAlreadyOccupiedException.
     */
    @Test
    void createAppointment_DebeLanzarException_CuandoElHorarioYaEstaOcupado() {
        // Arrange
        when(userRepository.findById(1L)).thenReturn(Optional.of(cliente));
        when(employeeRepository.findById(2L)).thenReturn(Optional.of(empleado));
        when(serviceRepository.findById(3L)).thenReturn(Optional.of(servicio));
        when(absenceRepository.isEmployeeOnLeave(2L, fechaLaborableFutura)).thenReturn(false);

        // Simulamos que el repositorio encuentra una cita existente (provocando un conflicto de solapamiento)
        Appointment citaExistente = new Appointment();
        when(appointmentRepository.checkAvailability(any(), any(), any(), any())).thenReturn(List.of(citaExistente));

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.createAppointment(requestValido))
                .isInstanceOf(SlotAlreadyOccupiedException.class)
                .hasMessageContaining("ya está ocupado para");
    }

    // =========================================================================
    // TESTS PARA DELETEAPPOINTMENT
    // =========================================================================

    /**
     * Verifica que si una cita existe en la base de datos, el método deleteAppointment
     * invoca correctamente el borrado por ID del componente repositorio.
     */
    @Test
    void deleteAppointment_DebeEliminar_CuandoCitaExiste() {
        // Arrange
        when(appointmentRepository.existsById(10L)).thenReturn(true);

        // Act
        appointmentService.deleteAppointment(10L);

        // Assert
        verify(appointmentRepository, times(1)).deleteById(10L);
    }

    /**
     * Asegura que el borrado de una cita devuelva una excepción si el ID introducido
     * no corresponde a ningún registro, evitando operaciones inválidas sobre la base de datos.
     */
    @Test
    void deleteAppointment_DebeLanzarException_CuandoCitaNoExiste() {
        // Arrange
        when(appointmentRepository.existsById(10L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> appointmentService.deleteAppointment(10L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("La cita introducida no existe");
        verify(appointmentRepository, never()).deleteById(any());
    }

    // =========================================================================
    // TESTS PARA GETAVAILABLEHOURS (ALGORITMO PRINCIPAL)
    // =========================================================================

    /**
     * Prueba el cortocircuito del algoritmo de disponibilidad horaria: si el barbero está de baja,
     * no calcula turnos ni tramos, devuelve una lista vacía de forma directa optimizando recursos.
     */
    @Test
    void getAvailableHours_DebeDevolverListaVacia_CuandoEmpleadoEstaDeBaja() {
        // Arrange
        when(absenceRepository.isEmployeeOnLeave(2L, fechaLaborableFutura)).thenReturn(true);

        // Act
        List<LocalTime> horasDisponibles = appointmentService.getAvailableHours(2L, fechaLaborableFutura, null);

        // Assert
        assertThat(horasDisponibles).isEmpty();
        verify(businessShiftRepository, never()).findByDayOfWeekOrderByStartTimeAsc(any());
    }

    /**
     * Evalúa la funcionalidad principal de la API: el cálculo de tramos horarios libres.
     * A partir de un turno de trabajo, comprueba que los tramos que chocan con citas existentes
     * sean correctamente eliminados de la lista de opciones que se enviará a la App Android.
     */
    @Test
    void getAvailableHours_DebeFiltrarTramosOcupados_Correctamente() {
        // Arrange
        when(absenceRepository.isEmployeeOnLeave(2L, fechaLaborableFutura)).thenReturn(false);

        // Definimos un turno de apertura simulado de 16:00 a 18:00
        BusinessShift turno = new BusinessShift();
        turno.setStartTime(LocalTime.of(16, 0));
        turno.setEndTime(LocalTime.of(18, 0));
        when(businessShiftRepository.findByDayOfWeekOrderByStartTimeAsc(fechaLaborableFutura.getDayOfWeek()))
                .thenReturn(List.of(turno));

        // Añadimos una cita real confirmada en la BD ocupando el espacio de 17:00 a 18:00
        Appointment citaOcupante = new Appointment();
        citaOcupante.setId(100L);
        citaOcupante.setStartTime(LocalTime.of(17, 0));
        citaOcupante.setEndTime(LocalTime.of(18, 0));
        citaOcupante.setStatus(AppoStatus.CONFIRMED);

        when(appointmentRepository.findByEmployeeIdAndDate(2L, fechaLaborableFutura))
                .thenReturn(List.of(citaOcupante));

        // Act (El algoritmo generará: 16:00, 16:30, 17:00, 17:30)
        List<LocalTime> horasDisponibles = appointmentService.getAvailableHours(2L, fechaLaborableFutura, null);

        // Assert (Los tramos de 17:00 y 17:30 deben filtrarse al estar comprometidos por la citaOcupante)
        assertThat(horasDisponibles).containsExactly(
                LocalTime.of(16, 0),
                LocalTime.of(16, 30)
        );
    }

    // =========================================================================
    // TESTS PARA CANCELAPPOINTMENT
    // =========================================================================

    /**
     * Valida el sistema de borrado lógico (Cancelación). Comprueba que al cancelar una cita confirmada,
     * su estado interno pasa a ser CANCELLED y se invoca la persistencia del repositorio.
     */
    @Test
    void cancelAppointment_DebeCambiarEstadoACancelado_CuandoEsCorrecto() {
        // Arrange
        Appointment citaATestear = new Appointment();
        citaATestear.setId(5L);
        citaATestear.setStatus(AppoStatus.CONFIRMED);
        when(appointmentRepository.getById(5L)).thenReturn(citaATestear);

        // Act
        appointmentService.cancelAppointment(5L);

        // Assert
        assertThat(citaATestear.getStatus()).isEqualTo(AppoStatus.CANCELLED);
        verify(appointmentRepository, times(1)).save(citaATestear);
    }
}