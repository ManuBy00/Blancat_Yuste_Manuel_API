package com.mby.myStore.Services;

import com.mby.myStore.Exceptions.DateNotValidException;
import com.mby.myStore.Model.BusinessShift;
import com.mby.myStore.Repositories.BusinessShiftRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Clase de pruebas unitarias para el servicio ShiftService.
 * Valida de forma aislada las reglas de negocio aplicadas a los turnos de apertura del establecimiento.
 */
@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private BusinessShiftRepository businessShiftRepository;

    @InjectMocks
    private ShiftService shiftService;

    private BusinessShift turnoManana;
    private BusinessShift turnoTarde;
    private Map<DayOfWeek, List<BusinessShift>> horarioCompletoValido;

    /**
     * Configuración del entorno de pruebas antes de cada ejecución.
     * Diseña una estructura base de turnos estándar (Mañana y Tarde) sin solapamientos.
     */
    @BeforeEach
    void setUp() {
        // Turno de mañana: 09:00 a 14:00
        turnoManana = new BusinessShift();
        turnoManana.setStartTime(LocalTime.of(9, 0));
        turnoManana.setEndTime(LocalTime.of(14, 0));

        // Turno de tarde: 16:00 a 20:00
        turnoTarde = new BusinessShift();
        turnoTarde.setStartTime(LocalTime.of(16, 0));
        turnoTarde.setEndTime(LocalTime.of(20, 0));

        // Estructura de mapa para simular la petición de actualización global
        horarioCompletoValido = new HashMap<>();
    }

    // =========================================================================
    // TESTS PARA UPDATEALLSCHEDULE
    // =========================================================================

    /**
     * Test de camino feliz (Happy Path).
     * Verifica que si el mapa de turnos cumple todas las reglas de validación cronológica,
     * el sistema limpie el cuadrante antiguo e inserte los nuevos turnos asignándoles su día.
     */
    @Test
    void updateAllSchedule_DebeActualizarHorario_CuandoTodosLosTurnosSonValidos() {
        // Arrange
        List<BusinessShift> turnosLunes = new ArrayList<>(List.of(turnoManana, turnoTarde));
        horarioCompletoValido.put(DayOfWeek.MONDAY, turnosLunes);

        // Act
        shiftService.updateAllSchedule(horarioCompletoValido);

        // Assert
        // Verificamos que se limpie la tabla por completo antes de meter el nuevo cuadrante
        verify(businessShiftRepository, times(1)).deleteAll();
        // Verificamos que se persistan los turnos validados
        verify(businessShiftRepository, times(1)).saveAll(turnosLunes);
    }

    /**
     * Valida la regla de integridad de un único turno.
     * Si la hora de inicio de un turno es posterior o igual a la de fin (ej: abre a las 18:00 y cierra a las 10:00),
     * el sistema debe denegar la transacción arrojando un DateNotValidException.
     */
    @Test
    void updateAllSchedule_DebeLanzarException_CuandoHoraInicioEsPosteriorAFin() {
        // Arrange
        BusinessShift turnoInvalido = new BusinessShift();
        turnoInvalido.setStartTime(LocalTime.of(18, 0));
        turnoInvalido.setEndTime(LocalTime.of(14, 0)); // Error: Cierra antes de abrir

        horarioCompletoValido.put(DayOfWeek.TUESDAY, new ArrayList<>(List.of(turnoInvalido)));

        // Act & Assert
        assertThatThrownBy(() -> shiftService.updateAllSchedule(horarioCompletoValido))
                .isInstanceOf(DateNotValidException.class)
                .hasMessageContaining("La hora de inicio")
                .hasMessageContaining("debe ser anterior a la de fin");

        // Al fallar la validación, nunca debe tocar la base de datos
        verify(businessShiftRepository, never()).deleteAll();
        verify(businessShiftRepository, never()).saveAll(anyList());
    }

    /**
     * Evalúa el algoritmo crítico de solapamiento de turnos en un mismo día.
     * Si el turno de tarde está configurado para empezar antes de que concluya el turno de mañana
     * (choque de horarios), el validador debe lanzar un DateNotValidException bloqueando el proceso.
     */
    @Test
    void updateAllSchedule_DebeLanzarException_CuandoLosTurnosSeSolapan() {
        // Arrange
        // Mañana: 09:00 a 14:00
        // Tarde: 13:30 a 18:00 (Error: Solapa media hora con el de la mañana)
        turnoTarde.setStartTime(LocalTime.of(13, 30));

        List<BusinessShift> turnosConChoque = new ArrayList<>(List.of(turnoManana, turnoTarde));
        horarioCompletoValido.put(DayOfWeek.WEDNESDAY, turnosConChoque);

        // Act & Assert
        assertThatThrownBy(() -> shiftService.updateAllSchedule(horarioCompletoValido))
                .isInstanceOf(DateNotValidException.class)
                .hasMessageContaining("no puede empezar antes de que termine el de mañana");

        verify(businessShiftRepository, never()).deleteAll();
        verify(businessShiftRepository, never()).saveAll(anyList());
    }

    /**
     * Comprueba la robustez del validador ante ordenaciones inversas en el Request.
     * Si el cliente envía el turno de tarde primero en la lista y el de mañana después,
     * el algoritmo `.sort()` incorporado en el servicio debe ordenarlos cronológicamente por su inicio
     * antes de evaluar el solapamiento, garantizando que la validación no falle por orden de inserción.
     */
    @Test
    void updateAllSchedule_DebeOrdenarLosTurnosCronologicamente_AntesDeValidarSolapamientos() {
        // Arrange
        // Forzamos un orden incorrecto en la lista de entrada: metemos la tarde antes que la mañana
        List<BusinessShift> turnosDesordenados = new ArrayList<>(List.of(turnoTarde, turnoManana));
        horarioCompletoValido.put(DayOfWeek.THURSDAY, turnosDesordenados);

        // Act
        shiftService.updateAllSchedule(horarioCompletoValido);

        // Assert
        // Si el método sort() no funcionara, habría saltado una excepción de solapamiento falsa.
        // Al pasar correctamente, se demuestra que los ordenó e identificó que son turnos válidos y limpios.
        verify(businessShiftRepository, times(1)).deleteAll();
        verify(businessShiftRepository, times(1)).saveAll(turnosDesordenados);
    }

    /**
     * Valida el comportamiento del sistema ante días sin actividad comercial (ej. Domingos cerrados).
     * Si un día del mapa llega con una lista vacía o nula, el sistema debe ignorar ese día de forma segura
     * sin lanzar excepciones de puntero nulo (NullPointerException) ni registrar nada para esa fecha.
     */
    @Test
    void updateAllSchedule_DebeIgnorarDiasVaciosONulos_SinLanzarException() {
        // Arrange
        horarioCompletoValido.put(DayOfWeek.SATURDAY, new ArrayList<>()); // Lista vacía
        horarioCompletoValido.put(DayOfWeek.SUNDAY, null);              // Lista nula

        // Act
        shiftService.updateAllSchedule(horarioCompletoValido);

        // Assert
        verify(businessShiftRepository, times(1)).deleteAll();
        // Al estar vacíos o nulos, el saveAll nunca debió llamarse ya que se salta el guardado de esos días
        verify(businessShiftRepository, never()).saveAll(anyList());
    }
}