package com.mby.myStore.Services;

import com.mby.myStore.DTO.InvoiceRequest;
import com.mby.myStore.DTO.InvoiceResponse;
import com.mby.myStore.Exceptions.RecordNotFoundException;
import com.mby.myStore.Model.*;
import com.mby.myStore.Repositories.AppointmentRepository;
import com.mby.myStore.Repositories.InvoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Clase de pruebas unitarias para el servicio InvoicesService.
 * Mediante Mockito, se asila la lógica de la capa de facturación y flujos de caja.
 */
@ExtendWith(MockitoExtension.class)
@SpringBootTest
class InvoicesServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    @InjectMocks
    private InvoicesService invoicesService;

    private Appointment citaValida;
    private InvoiceRequest requestValido;
    private Invoice facturaExistente;
    private LocalDate hoy;

    /**
     * Configuración del entorno antes de cada test.
     * Crea entidades espejo con relaciones internas (User, Service) completas para evitar NullPointerException.
     */
    @BeforeEach
    void setUp() {
        hoy = LocalDate.now();

        // 1. Configurar entidades anidadas de la cita
        User cliente = new User();
        cliente.setId(10L);
        cliente.setName("Manuel Gómez");

        Service servicio = new Service();
        servicio.setId(20L);
        servicio.setName("Corte Degradado");
        servicio.setPrice(15.50);

        // 2. Configurar Cita base en estado CONFIRMED (según lógica del servicio)
        citaValida = new Appointment();
        citaValida.setId(100L);
        citaValida.setStatus(AppoStatus.CONFIRMED);
        citaValida.setDate(hoy);
        citaValida.setUser(cliente);
        citaValida.setService(servicio);

        // 3. Configurar Request de Factura
        requestValido = new InvoiceRequest();
        requestValido.setAppointmentId(100L);
        requestValido.setPaymentMethod(paymentMethod.CARD);
        requestValido.setTotal(BigDecimal.valueOf(15.50));

        // 4. Configurar Factura ya guardada
        facturaExistente = new Invoice();
        facturaExistente.setId(1L);
        facturaExistente.setInvoiceNumber("INV-123456789");
        facturaExistente.setAppointment(citaValida);
        facturaExistente.setDate(hoy);
        facturaExistente.setPaymentMethod(paymentMethod.CARD);
        facturaExistente.setClientName("Manuel Gómez");
        facturaExistente.setServiceName("Corte Degradado");
        facturaExistente.setPrice(BigDecimal.valueOf(15.50));
    }

    // =========================================================================
    // TESTS PARA CREATEINVOICE
    // =========================================================================

    /**
     * Test de camino feliz para la generación de facturas.
     * Verifica que si la cita existe, está confirmada, y no tiene facturas previas,
     * se genere un registro con histórico inmutable (Snapshot) de nombres y precios.
     */
    @Test
    void createInvoice_DebeRegistrarExitosamente_CuandoCitaEsValidaYNoFacturada() {
        // Arrange
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(citaValida));
        when(invoiceRepository.findByAppointmentId(100L)).thenReturn(Optional.empty());
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(facturaExistente);

        // Act
        InvoiceResponse response = invoicesService.createInvoice(requestValido);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-123456789");
        assertThat(response.getClientName()).isEqualTo("Manuel Gómez");
        assertThat(response.getServiceName()).isEqualTo("Corte Degradado");
        assertThat(response.getPrice()).isEqualTo(BigDecimal.valueOf(15.50));
        verify(invoiceRepository, times(1)).save(any(Invoice.class));
    }

    /**
     * Comprueba que se deniegue la facturación si el ID de la cita no existe.
     */
    @Test
    void createInvoice_DebeLanzarException_CuandoCitaNoExiste() {
        // Arrange
        when(appointmentRepository.findById(100L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> invoicesService.createInvoice(requestValido))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("Cita no encontrada");
        verify(invoiceRepository, never()).save(any());
    }

    /**
     * Asegura que el sistema lance una excepción si se intenta emitir
     * una factura para una cita que ha sido previamente cancelada.
     */
    @Test
    void createInvoice_DebeLanzarException_CuandoCitaEstaCancelada() {
        // Arrange
        citaValida.setStatus(AppoStatus.CANCELLED);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(citaValida));

        // Act & Assert
        assertThatThrownBy(() -> invoicesService.createInvoice(requestValido))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("No se puede facturar una cita cancelada");
    }

    /**
     * Controla la regla de negocio explícita del servicio:
     * Lanza excepción si la cita ya consta con estado COMPLETADA.
     */
    @Test
    void createInvoice_DebeLanzarException_CuandoCitaEstaCompletada() {
        // Arrange
        citaValida.setStatus(AppoStatus.COMPLETED);
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(citaValida));

        // Act & Assert
        assertThatThrownBy(() -> invoicesService.createInvoice(requestValido))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("No se puede facturar una cita completada");
    }

    /**
     * Valida la restricción de unicidad: Una cita de la agenda solo puede
     * tener una única factura asociada para evitar duplicidad de cobros.
     */
    @Test
    void createInvoice_DebeLanzarException_CuandoCitaYaTieneFacturaAsociada() {
        // Arrange
        when(appointmentRepository.findById(100L)).thenReturn(Optional.of(citaValida));
        when(invoiceRepository.findByAppointmentId(100L)).thenReturn(Optional.of(facturaExistente));

        // Act & Assert
        assertThatThrownBy(() -> invoicesService.createInvoice(requestValido))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Esta cita ya tiene una factura asociada");
    }

    // =========================================================================
    // TESTS PARA GETBYID
    // =========================================================================

    @Test
    void getById_DebeRetornarResponse_CuandoIdExiste() {
        // Arrange
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(facturaExistente));

        // Act
        InvoiceResponse response = invoicesService.getById(1L);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-123456789");
    }

    @Test
    void getById_DebeLanzarException_CuandoIdInexistente() {
        // Arrange
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> invoicesService.getById(99L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("factura no encontrada");
    }

    // =========================================================================
    // TESTS PARA DELETEINVOICE
    // =========================================================================

    @Test
    void deleteInvoice_DebeEliminar_CuandoExiste() {
        // Arrange
        when(invoiceRepository.existsById(1L)).thenReturn(true);

        // Act
        invoicesService.deleteInvoice(1L);

        // Assert
        verify(invoiceRepository, times(1)).deleteById(1L);
    }

    @Test
    void deleteInvoice_DebeLanzarException_CuandoNoExiste() {
        // Arrange
        when(invoiceRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> invoicesService.deleteInvoice(99L))
                .isInstanceOf(RecordNotFoundException.class)
                .hasMessageContaining("La factura con ID 99 no existe.");
    }

    // =========================================================================
    // TESTS PARA UPDATEINVOICE
    // =========================================================================

    /**
     * Comprueba que la edición técnica de la factura preserve invariables
     * campos sensibles (código correlativo de factura) y modifique el método de pago o total.
     */
    @Test
    void updateInvoice_DebeModificarSoloCamposPermitidos() {
        // Arrange
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(facturaExistente));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(facturaExistente);

        InvoiceRequest edicion = new InvoiceRequest();
        edicion.setPaymentMethod(paymentMethod.CASH);
        edicion.setTotal(BigDecimal.valueOf(20.00));

        // Act
        InvoiceResponse response = invoicesService.updateInvoice(1L, edicion);

        // Assert
        assertThat(response).isNotNull();
        assertThat(facturaExistente.getPaymentMethod()).isEqualTo(paymentMethod.CASH);
        assertThat(facturaExistente.getPrice()).isEqualTo(BigDecimal.valueOf(20.00));
        verify(invoiceRepository, times(1)).save(facturaExistente);
    }

    // =========================================================================
    // TESTS METRICAS DE INGRESOS (INCOMES)
    // =========================================================================

    /**
     * Valida el cálculo de rendimientos económicos diarios en el Dashboard.
     * Simula múltiples facturas para una misma fecha y verifica que la suma acumulada
     * mediante Streams sume correctamente los BigDecimals.
     */
    @Test
    void incomesPerDay_DebeCalcularElSumatorioDeIngresosCorrectamente() {
        // Arrange
        Invoice f1 = new Invoice();
        f1.setPrice(BigDecimal.valueOf(15.50));
        Invoice f2 = new Invoice();
        f2.setPrice(BigDecimal.valueOf(10.00));

        when(invoiceRepository.findByDate(hoy)).thenReturn(List.of(f1, f2));

        // Act
        BigDecimal totalIngresos = invoicesService.incomesPerDay(hoy);

        // Assert
        assertThat(totalIngresos).isEqualTo(BigDecimal.valueOf(25.50));
    }

    @Test
    void incomesPerDay_DebeRetornarCero_CuandoNoHayFacturasElDiaDeLaFecha() {
        // Arrange
        when(invoiceRepository.findByDate(hoy)).thenReturn(Collections.emptyList());

        // Act
        BigDecimal totalIngresos = invoicesService.incomesPerDay(hoy);

        // Assert
        assertThat(totalIngresos).isEqualTo(BigDecimal.ZERO);
    }
}