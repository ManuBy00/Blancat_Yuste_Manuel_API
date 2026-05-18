package com.mby.myStore.Services;

import com.mby.myStore.DTO.AbsenceRequest;
import com.mby.myStore.DTO.AbsenceResponse;
import com.mby.myStore.Exceptions.DateNotValidException;
import com.mby.myStore.Exceptions.RecordNotFoundException;
import com.mby.myStore.Model.Absence;
import com.mby.myStore.Model.Employee;
import com.mby.myStore.Repositories.AbsenceRepository;
import com.mby.myStore.Repositories.AppointmentRepository;
import com.mby.myStore.Repositories.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

    /**
     * Clase de pruebas unitarias para el servicio AbsencesService.
     * Aísla la lógica de negocio simulando la persistencia y los servicios asociados mediante Mockito.
     */
    @ExtendWith(MockitoExtension.class)
    @SpringBootTest
    class AbsencesServiceTest {

        @Mock
        private AbsenceRepository absenceRepository;
        @Mock
        private EmployeeRepository employeeRepository;
        @Mock
        private AppointmentService appointmentService;
        @Mock
        private AppointmentRepository appointmentRepository; // Inyectado por consistencia técnica con el servicio

        @InjectMocks
        private AbsencesService absencesService;

        private Employee empleado;
        private AbsenceRequest requestValido;
        private Absence ausenciaExistente;
        private LocalDate hoy;

        /**
         * Inicialización del entorno de pruebas.
         * Configura instancias base reutilizables asociando fechas dinámicas relativas a "hoy".
         */
        @BeforeEach
        void setUp() {
            hoy = LocalDate.now();

            empleado = new Employee();
            empleado.setId(1L);
            empleado.setName("Barbero Javier");

            requestValido = new AbsenceRequest();
            requestValido.setEmployeeId(1L);
            requestValido.setStartDate(hoy.plusDays(2)); // Fecha futura válida
            requestValido.setEndDate(hoy.plusDays(5));   // Rango coherente
            requestValido.setReason("Asistencia a curso de formación");

            ausenciaExistente = new Absence();
            ausenciaExistente.setId(100L);
            ausenciaExistente.setEmployee(empleado);
            ausenciaExistente.setStartDate(hoy.plusDays(1));
            ausenciaExistente.setEndDate(hoy.plusDays(3));
            ausenciaExistente.setReason("Vacaciones anuales");
        }

        // =========================================================================
        // TESTS PARA CREATEABSENCE
        // =========================================================================

        /**
         * Test de camino feliz (Happy Path) para la creación de ausencias.
         * Verifica que si el empleado existe y las fechas son correctas, se procese la baja,
         * se cancelen las citas agendadas en ese periodo y se salve el registro.
         */
        @Test
        void createAbsence_DebeRegistrarExitosamente_CuandoTodoEsValido() {
            // Arrange
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(empleado));
            when(absenceRepository.save(any(Absence.class))).thenReturn(ausenciaExistente);

            // Act
            AbsenceResponse response = absencesService.createAbsence(requestValido);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getEmployeeId()).isEqualTo(1L);
            assertThat(response.getEmployeeName()).isEqualTo("Barbero Javier");
            assertThat(response.getReason()).isEqualTo("Asistencia a curso de formación");

            // Comprobar que se ejecutó la cancelación masiva de citas asociadas a este periodo de ausencia
            verify(appointmentService, times(1))
                    .cancelEmployeeAppointmentsByPeriod(empleado.getId(), requestValido.getStartDate(), requestValido.getEndDate());
            verify(absenceRepository, times(1)).save(any(Absence.class));
        }

        /**
         * Verifica que el sistema aborte la operación y lance RecordNotFoundException
         * si se intenta dar de baja a un identificador de empleado que no consta en el sistema.
         */
        @Test
        void createAbsence_DebeLanzarException_CuandoEmpleadoNoExiste() {
            // Arrange
            when(employeeRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> absencesService.createAbsence(requestValido))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessageContaining("Empleado no encontrado");

            verify(absenceRepository, never()).save(any());
            verify(appointmentService, never()).cancelEmployeeAppointmentsByPeriod(any(), any(), any());
        }

        /**
         * Valida la regla semántica de ordenación de fechas:
         * No se puede registrar una ausencia cuya fecha de fin ocurra antes que la de inicio.
         */
        @Test
        void createAbsence_DebeLanzarException_CuandoFechaInicioEsPosteriorAFin() {
            // Arrange
            requestValido.setStartDate(hoy.plusDays(5));
            requestValido.setEndDate(hoy.plusDays(2)); // Conflicto temporal
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(empleado));

            // Act & Assert
            assertThatThrownBy(() -> absencesService.createAbsence(requestValido))
                    .isInstanceOf(DateNotValidException.class)
                    .hasMessageContaining("La fecha de inicio no puede ser posterior a la de fin");
        }

        /**
         * Comprueba las restricciones de integridad cronológica.
         * No se permite registrar justificaciones de ausencia orientadas a fechas del pasado.
         */
        @Test
        void createAbsence_DebeLanzarException_CuandoFechaInicioEsPasada() {
            // Arrange
            requestValido.setStartDate(hoy.minusDays(2)); // Fecha del pasado
            requestValido.setEndDate(hoy.plusDays(2));
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(empleado));

            // Act & Assert
            assertThatThrownBy(() -> absencesService.createAbsence(requestValido))
                    .isInstanceOf(DateNotValidException.class)
                    .hasMessageContaining("La fecha de inicio no puede ser anterior a hoy");
        }

        // =========================================================================
        // TESTS PARA UPDATEABSENCE
        // =========================================================================

        /**
         * Test de camino feliz para la edición de ausencias.
         * Comprueba que tras validar la existencia del ID de la ausencia y la coherencia de fechas,
         * la entidad se sobrescriba y actualice correctamente.
         */
        @Test
        void updateAbsence_DebeActualizar_CuandoIdYFechasSonValidos() {
            // Arrange
            when(absenceRepository.findById(100L)).thenReturn(Optional.of(ausenciaExistente));

            AbsenceRequest nuevosDatos = new AbsenceRequest();
            nuevosDatos.setStartDate(hoy.plusDays(3));
            nuevosDatos.setEndDate(hoy.plusDays(7));
            nuevosDatos.setReason("Motivos médicos ampliados");

            // Act
            AbsenceResponse response = absencesService.updateAbsence(100L, nuevosDatos);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getId()).isEqualTo(100L);
            assertThat(response.getReason()).isEqualTo("Motivos médicos ampliados");
            assertThat(response.getStartDate()).isEqualTo(hoy.plusDays(3));
            verify(absenceRepository, times(1)).save(ausenciaExistente);
        }

        /**
         * Comprueba que se impida la edición de los datos de una ausencia si el identificador
         * proporcionado no coincide con ningún registro en el histórico del sistema.
         */
        @Test
        void updateAbsence_DebeLanzarException_CuandoAusenciaNoExiste() {
            // Arrange
            when(absenceRepository.findById(999L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> absencesService.updateAbsence(999L, requestValido))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessageContaining("Ausencia no encontrada con ID: 999");
        }

        // =========================================================================
        // TESTS PARA DELETEABSENCE
        // =========================================================================

        /**
         * Verifica que si una ausencia existe, se llame de forma directa al borrado físico
         * de la misma en el repositorio.
         */
        @Test
        void deleteAbsence_DebeEliminar_CuandoExiste() {
            // Arrange
            when(absenceRepository.existsById(100L)).thenReturn(true);

            // Act
            absencesService.deleteAbsence(100L);

            // Assert
            verify(absenceRepository, times(1)).deleteById(100L);
        }

        /**
         * Valida la seguridad ante borrados huérfanos. Si se intenta eliminar una baja médica o vacaciones
         * inexistentes, arroja una excepción sin realizar llamadas destructivas de datos.
         */
        @Test
        void deleteAbsence_DebeLanzarException_CuandoNoExiste() {
            // Arrange
            when(absenceRepository.existsById(100L)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> absencesService.deleteAbsence(100L))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessageContaining("No se puede borrar: Ausencia no encontrada");

            verify(absenceRepository, never()).deleteById(any());
        }

        // =========================================================================
        // TESTS PARA GETABSENCESBYEMPLOYEE
        // =========================================================================

        /**
         * Evalúa la recuperación de información. Si el barbero existe, retorna su histórico
         * de ausencias estructurado bajo el formato óptimo del DTO de respuesta.
         */
        @Test
        void getAbsencesByEmployee_DebeDevolverLista_CuandoEmpleadoExiste() {
            // Arrange
            when(employeeRepository.findById(1L)).thenReturn(Optional.of(empleado));
            when(absenceRepository.getAbsenceByEmployeeId(1L)).thenReturn(List.of(ausenciaExistente));

            // Act
            List<AbsenceResponse> resultado = absencesService.getAbsencesByEmployee(1L);

            // Assert
            assertThat(resultado).isNotEmpty().hasSize(1);
            assertThat(resultado.get(0).getId()).isEqualTo(100L);
            assertThat(resultado.get(0).getEmployeeName()).isEqualTo("Barbero Javier");
        }

        /**
         * Comprueba que la consulta del listado de ausencias falle controladamente si el identificador
         * del profesional consultado no corresponde a un perfil registrado de la plantilla.
         */
        @Test
        void getAbsencesByEmployee_DebeLanzarException_CuandoEmpleadoNoExiste() {
            // Arrange
            when(employeeRepository.findById(1L)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> absencesService.getAbsencesByEmployee(1L))
                    .isInstanceOf(RecordNotFoundException.class)
                    .hasMessageContaining("Empleado no encontrado");
        }
    }

