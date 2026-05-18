package com.mby.myStore.Services;

import com.mby.myStore.Model.Service;
import com.mby.myStore.Repositories.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Clase de pruebas unitarias para el servicio ServicesService.
 * Valida de forma aislada la gestión del catálogo de servicios de la barbería.
 */
@ExtendWith(MockitoExtension.class)
class ServicesServiceTest {

    @Mock
    private ServiceRepository serviceRepository;

    @InjectMocks
    private ServicesService servicesService;

    private Service servicioBase;
    private Service datosEditados;

    /**
     * Configuración del entorno de pruebas.
     * Define instancias de prueba válidas antes de la ejecución de cada test.
     */
    @BeforeEach
    void setUp() {
        servicioBase = new Service();
        servicioBase.setId(1L);
        servicioBase.setName("Corte de Pelo Clásico");
        servicioBase.setPrice(12.00);
        servicioBase.setDurationMinutes(30L);

        datosEditados = new Service();
        datosEditados.setName("Corte de Pelo + Lavado Premium");
        datosEditados.setPrice(18.50);
        datosEditados.setDurationMinutes(45L);
    }

    // =========================================================================
    // TESTS PARA GETSERVICIOS
    // =========================================================================

    /**
     * Verifica que si hay servicios guardados en el catálogo, el método
     * retorne el listado completo correctamente.
     */
    @Test
    void getServicios_DebeDevolverLista_CuandoExistenRegistros() {
        // Arrange
        when(serviceRepository.findAll()).thenReturn(List.of(servicioBase));

        // Act
        List<Service> resultado = servicesService.getServicios();

        // Assert
        assertThat(resultado).isNotEmpty().hasSize(1);
        assertThat(resultado.get(0).getName()).isEqualTo("Corte de Pelo Clásico");
        // Nota técnica: Tu lógica llama a findAll() dos veces (una para el isEmpty y otra en el return)
        verify(serviceRepository, times(2)).findAll();
    }

    /**
     * Asegura que si la base de datos está vacía, el método devuelva una
     * lista vacía inicializada en lugar de un valor nulo para prevenir excepciones en los clientes.
     */
    @Test
    void getServicios_DebeDevolverListaVacia_CuandoNoHayRegistros() {
        // Arrange
        when(serviceRepository.findAll()).thenReturn(Collections.emptyList());

        // Act
        List<Service> resultado = servicesService.getServicios();

        // Assert
        assertThat(resultado).isNotNull().isEmpty();
        verify(serviceRepository, times(1)).findAll(); // Se detiene en el primer condicional
    }

    // =========================================================================
    // TESTS PARA CREATESERVICE
    // =========================================================================

    /**
     * Test de camino feliz para el alta de servicios.
     * Valida que un servicio con nombre, precio y duración coherente se guarde correctamente.
     */
    @Test
    void createService_DebeGuardarExitosamente_CuandoElServicioEsValido() {
        // Arrange
        when(serviceRepository.save(any(Service.class))).thenReturn(servicioBase);

        // Act
        Service resultado = servicesService.createService(servicioBase);

        // Assert
        assertThat(resultado).isNotNull();
        assertThat(resultado.getPrice()).isEqualTo(12.00);
        verify(serviceRepository, times(1)).save(servicioBase);
    }

    /**
     * Protege el sistema frente a incoherencias de negocio en la creación.
     * Si el precio del servicio es negativo, debe lanzar un IllegalArgumentException.
     */
    @Test
    void createService_DebeLanzarException_CuandoPrecioEsNegativo() {
        // Arrange
        servicioBase.setPrice(-5.00); // Precio inválido

        // Act & Assert
        assertThatThrownBy(() -> servicesService.createService(servicioBase))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El precio no puede ser negativo");

        verify(serviceRepository, never()).save(any());
    }

    // =========================================================================
    // TESTS PARA DELETESERVICE
    // =========================================================================

    /**
     * Comprueba que si el ID del servicio existe en el catálogo, se procese
     * el borrado físico delegando la instrucción al repositorio.
     */
    @Test
    void deleteService_DebeEliminar_CuandoIdExiste() {
        // Arrange
        when(serviceRepository.existsById(1L)).thenReturn(true);

        // Act
        servicesService.deleteService(1L);

        // Assert
        verify(serviceRepository, times(1)).deleteById(1L);
    }

    /**
     * Valida que si se intenta borrar un ID que no consta en el sistema,
     * salte controladamente una excepción EntityNotFoundException sin hacer llamadas destructivas.
     */
    @Test
    void deleteService_DebeLanzarException_CuandoIdNoExiste() {
        // Arrange
        when(serviceRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> servicesService.deleteService(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("El servicio con ID 99 no existe.");

        verify(serviceRepository, never()).deleteById(any());
    }

    // =========================================================================
    // TESTS PARA UPDATESERVICE
    // =========================================================================

    /**
     * Test de camino feliz para la edición de servicios.
     * Comprueba que se recupera el registro original, se sobrescriben únicamente
     * los campos modificables y se guardan los cambios de forma correcta.
     */
    @Test
    void updateService_DebeActualizarCampos_CuandoTodosLosDatosSonValidos() {
        // Arrange
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicioBase));
        when(serviceRepository.save(any(Service.class))).thenReturn(servicioBase);

        // Act
        Service resultado = servicesService.updateService(1L, datosEditados);

        // Assert
        assertThat(resultado).isNotNull();
        assertThat(resultado.getId()).isEqualTo(1L); // El ID se mantiene
        assertThat(resultado.getName()).isEqualTo("Corte de Pelo + Lavado Premium");
        assertThat(resultado.getPrice()).isEqualTo(18.50);
        assertThat(resultado.getDurationMinutes()).isEqualTo(45L);
        verify(serviceRepository, times(1)).save(servicioBase);
    }

    /**
     * Verifica que no se pueda actualizar un servicio si su identificador
     * no pertenece a ningún registro existente en el repositorio.
     */
    @Test
    void updateService_DebeLanzarException_CuandoServicioNoExiste() {
        // Arrange
        when(serviceRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> servicesService.updateService(99L, datosEditados))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("El servicio con ID 99 no existe.");

        verify(serviceRepository, never()).save(any());
    }

    /**
     * Valida el blindaje del método de edición frente a precios inválidos en el request.
     */
    @Test
    void updateService_DebeLanzarException_CuandoPrecioEnEdicionEsNegativo() {
        // Arrange
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicioBase));
        datosEditados.setPrice(-10.00); // Precio inválido

        // Act & Assert
        assertThatThrownBy(() -> servicesService.updateService(1L, datosEditados))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("El precio del servicio no puede ser negativo.");

        verify(serviceRepository, never()).save(any());
    }

    /**
     * Valida el blindaje cronológico del método de edición. La duración mínima de un
     * servicio para poder agendar bloques de citas en la agenda debe ser estrictamente mayor que 0.
     */
    @Test
    void updateService_DebeLanzarException_CuandoDuracionEnEdicionEsMenorOIgualACero() {
        // Arrange
        when(serviceRepository.findById(1L)).thenReturn(Optional.of(servicioBase));
        datosEditados.setDurationMinutes(0L); // Duración inválida (0 o negativa)

        // Act & Assert
        assertThatThrownBy(() -> servicesService.updateService(1L, datosEditados))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("La duración debe ser mayor a 0 minutos.");

        verify(serviceRepository, never()).save(any());
    }
}