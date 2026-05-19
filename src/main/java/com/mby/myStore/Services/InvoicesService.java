package com.mby.myStore.Services;

import com.mby.myStore.DTO.InvoiceRequest;
import com.mby.myStore.DTO.InvoiceResponse;
import com.mby.myStore.Exceptions.RecordNotFoundException;
import com.mby.myStore.Model.AppoStatus;
import com.mby.myStore.Model.Appointment;
import com.mby.myStore.Model.Invoice;
import com.mby.myStore.Repositories.AppointmentRepository;
import com.mby.myStore.Repositories.InvoiceRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class InvoicesService {

    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    public InvoiceResponse createInvoice(InvoiceRequest invoice) {
        Appointment appointment = appointmentRepository.findById(invoice.getAppointmentId())
                .orElseThrow(() -> new RecordNotFoundException("Cita no encontrada"));

        if (appointment.getStatus() == AppoStatus.CANCELLED){
            throw new RecordNotFoundException("No se puede facturar una cita cancelada");
        }

        if (appointment.getStatus() == AppoStatus.COMPLETED){
            throw new RecordNotFoundException("No se puede facturar una cita completada");
        }

        if (invoiceRepository.findByAppointmentId(appointment.getId()).isPresent()) {
            throw new IllegalStateException("Esta cita ya tiene una factura asociada");
        }



        Invoice invoiceEntity = new Invoice();
        invoiceEntity.setAppointment(appointment);
        invoiceEntity.setDate(appointment.getDate());
        invoiceEntity.setPaymentMethod(invoice.getPaymentMethod());
        invoiceEntity.setInvoiceNumber("INV-" + System.currentTimeMillis());
        //snapshots
        invoiceEntity.setClientName(appointment.getUser().getName());
        invoiceEntity.setEmployeeName(appointment.getEmployee().getName());
        invoiceEntity.setServiceName(appointment.getService().getName());
        invoiceEntity.setPrice(BigDecimal.valueOf(appointment.getService().getPrice()));

        Invoice saved = invoiceRepository.save(invoiceEntity);

        return toResponse(saved);
    }

    public InvoiceResponse getById(Long id) {
        Invoice invoice = invoiceRepository.findById(id).orElseThrow(() -> new RecordNotFoundException("factura no encontrada"));
        return toResponse(invoice);
    }

    public List<InvoiceResponse> getInvoicesByDate(LocalDate date) {
        return invoiceRepository.findByDate(date)
                .stream()
                .map(this::toResponse) // Usamos el método de mapeo que ya tienes
                .toList();
    }

    public void deleteInvoice(Long id) {
        // 1. Verificar si existe
        if (!invoiceRepository.existsById(id)) {
            throw new RecordNotFoundException("La factura con ID " + id + " no existe.");
        }
        // 2. Borrar
        invoiceRepository.deleteById(id);
    }

    public InvoiceResponse updateInvoice(Long id, InvoiceRequest request) {
        // 1. Buscar la factura existente
        Invoice invoice = invoiceRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("Factura no encontrada"));

        // 2. Actualizar solo los campos permitidos
        // No solemos dejar cambiar el 'appointment' ni el 'invoiceNumber' una vez creada
        invoice.setPaymentMethod(request.getPaymentMethod());
        invoice.setPrice(request.getTotal());

        // 3. Guardar y devolver
        Invoice updated = invoiceRepository.save(invoice);
        return toResponse(updated);
    }

    public Invoice getInvoiceEntityById(Long id){
        return invoiceRepository.findById(id).orElseThrow(() -> new RecordNotFoundException("Factura no encontrada"));
    }

    public BigDecimal incomesPerDay(LocalDate date) {
        List<Invoice> incomes = invoiceRepository.findByDate(date);

        BigDecimal total = incomes.stream().map(Invoice::getPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        return total;
    }

    public Integer countByDate(LocalDate date) {
        return invoiceRepository.countByDate(date);
    }

    public Invoice findByAppointmentId(Long appointmentId) {
        if (appointmentRepository.findById(appointmentId).isPresent()) {
            return invoiceRepository.findByAppointmentId(appointmentId).get();
        }else{
            throw new RecordNotFoundException("No existe factura para la cita con id " + appointmentId);
        }
    }



    public InvoiceResponse toResponse(Invoice entity) {
        InvoiceResponse dto = new InvoiceResponse();
        dto.setId(entity.getId());
        dto.setInvoiceNumber(entity.getInvoiceNumber());
        dto.setDate(entity.getDate());
        dto.setPrice(entity.getPrice());
        dto.setPaymentMethod(entity.getPaymentMethod());
        dto.setServiceName(entity.getServiceName());
        dto.setClientName(entity.getClientName());
        dto.setEmployeeName(entity.getEmployeeName());

        return dto;
    }
}
