package com.mby.myStore.Services;


import com.mby.myStore.DTO.AppointmentRequest;
import com.mby.myStore.DTO.AppointmentResponse;
import com.mby.myStore.DTO.ServiceCountDTO;
import com.mby.myStore.Exceptions.DateNotValidException;
import com.mby.myStore.Exceptions.RecordNotFoundException;
import com.mby.myStore.Exceptions.SlotAlreadyOccupiedException;
import com.mby.myStore.Model.*;
import com.mby.myStore.Repositories.*;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@org.springframework.stereotype.Service
@Transactional
public class AppointmentService {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ServiceRepository serviceRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private AbsenceRepository absenceRepository;

    @Autowired
    private BusinessShiftRepository businessShiftRepository;

    /**
     * Registra una nueva cita calculando automáticamente la duración y validando disponibilidad.
     * @param dto Objeto con los datos de la reserva (Fecha, Hora Inicio, IDs de relaciones).
     * @throws SlotAlreadyOccupiedException Si el horario ya está comprometido para ese empleado.
     */
    public AppointmentResponse createAppointment(AppointmentRequest dto) throws SlotAlreadyOccupiedException {
        //  Carga de dependencias con mensajes de error
        User user = userRepository.findById(dto.getClientId())
                .orElseThrow(() -> new RecordNotFoundException("Cliente ID " + dto.getClientId() + " no encontrado"));
        Employee employee = employeeRepository.findById(dto.getEmployeeId())
                .orElseThrow(() -> new RecordNotFoundException("Empleado ID " + dto.getEmployeeId() + " no encontrado"));
        Service service = serviceRepository.findById(dto.getServiceId())
                .orElseThrow(() -> new RecordNotFoundException("Servicio ID " + dto.getServiceId() + " no encontrado"));

        checkAbsence(dto.getEmployeeId(), dto.getDate());

        //validamos que la fecha no sea anterior a hoy
        if (dto.getDate().isBefore(LocalDate.now())) {
            throw new DateNotValidException("No se pueden programar citas en fechas pasadas.");
        }



        //  Construcción del objeto (Mapeo)
        Appointment appointment = new Appointment();
        appointment.setDate(dto.getDate());
        appointment.setStartTime(dto.getStartTime());
        appointment.setUser(user);
        appointment.setEmployee(employee);
        appointment.setService(service);

        // Valores automáticos
        appointment.setCreatedAt(Instant.now());
        appointment.setStatus(AppoStatus.CONFIRMED);

        // Cálculo de fin basado en la duración del servicio recuperado
        appointment.setEndTime(dto.getStartTime().plusMinutes(service.getDurationMinutes()));

        //  Validación de Negocio: Se hace justo antes de guardar
        if (!checkAvailability(appointment)) {
            throw new SlotAlreadyOccupiedException("El horario solicitado (" +
                    appointment.getStartTime() + " - " + appointment.getEndTime() + ") ya está ocupado para " + employee.getName());
        }

        appointmentRepository.save(appointment);

        return mapToViewDTO(appointment);
    }

    /**
     * Elimina una cita existente tras verificar su presencia en la base de datos.
     */
    public void deleteAppointment(Long id) {
        if (appointmentRepository.existsById(id)){
            appointmentRepository.deleteById(id);
        }else {
            throw new RecordNotFoundException("La cita introducida no existe");
        }
    }

    /**
     * Actualiza una cita permitiendo cambiar fecha, hora, empleado o servicio.
     * @param id Identificador de la cita original.
     * @param citaEditada Objeto con los nuevos valores.
     */
    @Transactional
    public AppointmentResponse updateAppointment(Long id, AppointmentRequest citaEditada) throws SlotAlreadyOccupiedException {
        // Verificamos que la cita existe
        Appointment appointmentExistente = appointmentRepository.findById(id)
                .orElseThrow(() -> new RecordNotFoundException("No se encontró la cita con ID: " + id));

        //  Cargamos las nuevas dependencias (Empleado y Servicio)
        Service service = serviceRepository.findById(citaEditada.getServiceId())
                .orElseThrow(() -> new RecordNotFoundException("Servicio no encontrado"));
        Employee employee = employeeRepository.findById(citaEditada.getEmployeeId())
                .orElseThrow(() -> new RecordNotFoundException("Empleado no encontrado"));

        // Calculamos la nueva hora de fin
        LocalTime nuevaHoraFin = citaEditada.getStartTime().plusMinutes(service.getDurationMinutes());

        //validación de fecha
        checkAbsence(citaEditada.getId(), citaEditada.getDate());

        if (citaEditada.getDate().isBefore(LocalDate.now())) {
            throw new DateNotValidException("No se pueden programar citas en fechas pasadas.");
        }

        // Validar que no sea Fin de Semana
        DayOfWeek dia = citaEditada.getDate().getDayOfWeek();
        if (dia == DayOfWeek.SATURDAY || dia == DayOfWeek.SUNDAY) {
            throw new DateNotValidException("No abrimos los fines de semana. Por favor, elige un día de lunes a viernes.");
        }

        // Validación de solapamiento (Excluyendo la propia cita que editamos)
        boolean hayChoqueReal = appointmentRepository.checkAvailability(
                        employee, citaEditada.getStartTime(), nuevaHoraFin, citaEditada.getDate())
                .stream().anyMatch(c -> c.getId() != id);

        if (hayChoqueReal) {
            throw new SlotAlreadyOccupiedException("El barbero ya tiene otra cita en ese horario.");
        }

        // Actualizamos los datos de la Entidad
        appointmentExistente.setDate(citaEditada.getDate());
        appointmentExistente.setStartTime(citaEditada.getStartTime());
        appointmentExistente.setEndTime(nuevaHoraFin);
        appointmentExistente.setEmployee(employee);
        appointmentExistente.setService(service);
        appointmentExistente.setStatus(citaEditada.getStatus());

        // Guardamos y devolvemos el DTO básico (Usando tu método entityToDTO)
        appointmentRepository.save(appointmentExistente);
        return mapToViewDTO(appointmentExistente);
    }


    public List<AppointmentResponse> getAppointmentsByDate(LocalDate date) {
        List<Appointment> appointments = appointmentRepository.getAppointmentsByDate(date);

        // Convertimos la lista de Entidades a DTOs de vista
        return appointments.stream()
                .map(this::mapToViewDTO)
                .collect(Collectors.toList());
    }



    boolean checkAvailability(Appointment appointment) {
        return appointmentRepository.checkAvailability(appointment.getEmployee(), appointment.getStartTime(), appointment.getEndTime(), appointment.getDate()).isEmpty();
    }

    /**
     * Algoritmo principal para mostrar la agenda disponible en la App Android. permitiendo excluir una cita específica (útil para ediciones).
     * Genera tramos horarios y los filtra comparándolos con las citas reales del día.
     */
    public List<LocalTime> getAvailableHours(Long empleadoId, LocalDate fecha, Integer excludeId) {
        //validar si el empleado está de baja
        if (absenceRepository.isEmployeeOnLeave(empleadoId, fecha)) {
            return Collections.emptyList();
        }

        if (fecha.isBefore(LocalDate.now())) {
            return Collections.emptyList();
        }

        // obtenemos los turnos para ese día de lasemana
        List<BusinessShift> turnos = businessShiftRepository.findByDayOfWeekOrderByStartTimeAsc(fecha.getDayOfWeek());

        // Generar la lista de tramos uniendo todos los turnos
        List<LocalTime> todosLosTramos = new ArrayList<>();
        for (BusinessShift turno : turnos) {
            todosLosTramos.addAll(generateSlotsForInterval(turno.getStartTime(), turno.getEndTime()));
        }

        // 2. Traemos todas las citas de la base de datos
        List<Appointment> citasDB = appointmentRepository.findByEmployeeIdAndDate(empleadoId, fecha);


        // FILTRO : Quitamos las canceladas y, si existe, la que queremos excluir
        List<Appointment> citasParaValidar = citasDB.stream()
                .filter(a -> a.getStatus() != AppoStatus.CANCELLED) // <--- Ignoramos canceladas
                .filter(a -> excludeId == null || !a.getId().equals(Long.valueOf(excludeId))) // <--- Filtro de edición
                .collect(Collectors.toList());

        // 4. Filtramos los tramos usando la lista que ya es segura para el lambda
        return todosLosTramos.stream()
                .filter(tramo -> isAvailable(tramo, citasParaValidar))
                .collect(Collectors.toList());
    }

    /**
     * Función auxiliar que determina si un punto específico en el tiempo (tramo)
     * cae dentro del intervalo [Inicio, Fin) de alguna cita ocupada.
     */
    private boolean isAvailable(LocalTime tramo, List<Appointment> appointments) {
        for (Appointment appointment : appointments) {
            // Si el tramo cae dentro de una cita, no está libre
            if (!tramo.isBefore(appointment.getStartTime()) && tramo.isBefore(appointment.getEndTime())) {
                return false;
            }
        }
        return true; // Si recorre todas y no choca, está libre
    }

    /**
     * Lógica de negocio para buscar citas por el nombre de un cliente.
     * @param nombre Cadena de texto a buscar (nombre del cliente).
     * @return Lista de citas que coinciden con el criterio.
     */
    public List<AppointmentResponse> buscarCitasPorNombreCliente(String nombre) {
        // Llamamos al método con la @Query personalizada que creamos en el Repository
        return appointmentRepository.findByUserName(nombre).stream().map(this::mapToViewDTO).toList();
    }

    public List<AppointmentResponse> getAppoByUserId(Long userId) {
        return appointmentRepository.findByUserId(userId).stream().map(this::mapToViewDTO).toList();
    }

    public void cancelAppointment(Long id) {
        Appointment appointment = appointmentRepository.getById(id);
        if (appointment != null && appointment.getStatus() == AppoStatus.CONFIRMED) {
            appointment.setStatus(AppoStatus.CANCELLED);
            appointmentRepository.save(appointment);
        }else{
            throw new DateNotValidException("No se pueden cancelar la cita");
        }
    }

    /**
     * Convierte una entidad Cita en un objeto CitaDTO.
     * Extrae solo los IDs de las relaciones para simplificar la respuesta.
     */
    public AppointmentRequest mapToDTO(Appointment appointment) {
        AppointmentRequest dto = new AppointmentRequest();

        // Copiamos los datos básicos
        dto.setDate(appointment.getDate());
        dto.setStartTime(appointment.getStartTime());

        // Extraemos los IDs de los objetos relacionados
        dto.setClientId(appointment.getUser().getId());
        dto.setEmployeeId(appointment.getEmployee().getId());
        dto.setServiceId(appointment.getService().getId());

        return dto;
    }



    private AppointmentResponse mapToViewDTO(Appointment entity) {
        AppointmentResponse dto = new AppointmentResponse();
        dto.setId(entity.getId());
        dto.setDate(entity.getDate());
        dto.setStartTime(entity.getStartTime());
        dto.setEmployeeId(entity.getEmployee().getId());
        dto.setServiceId(entity.getService().getId());
        dto.setCustomerId(entity.getUser().getId());

        // Extraemos los nombres de las relaciones
        dto.setCustomerName(entity.getUser().getName());
        dto.setServiceName(entity.getService().getName());
        dto.setEmployeeName(entity.getEmployee().getName());
        dto.setTelNumber(entity.getUser().getTelNumber());
        dto.setStatus(entity.getStatus());
        dto.setEmployeeId(entity.getEmployee().getId());

        // Datos extra para la card
        dto.setDurationMinutes(entity.getService().getDurationMinutes());
        dto.setPrice(entity.getService().getPrice());

        return dto;
    }

    public List<ServiceCountDTO> getTodayServiceStats() {
        return appointmentRepository.countServicesPerDay(LocalDate.now());
    }

    public void checkAbsence(Long idEmpleado, LocalDate fecha) {
        if (absenceRepository.isEmployeeOnLeave(idEmpleado, fecha)) {
            throw new DateNotValidException("El profesional no está disponible en la fecha seleccionada por motivos de baja o vacaciones.");
        }
    }

    public int cancelEmployeeAppointmentsByPeriod(Long employeeId, LocalDate start, LocalDate end) {
        // Validación de seguridad: que la fecha de inicio no sea mayor que la de fin
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("La fecha de inicio no puede ser posterior a la de fin");
        }

        // Ejecutamos la cancelación masiva
        int affectedRows = appointmentRepository.cancelAppointmentsInPeriod(employeeId, start, end);

        return affectedRows;
    }

    public int countTotalAvailableSlots(LocalDate fecha) {
        // 1. Obtenemos todos los empleados (barberos)
        List<Employee> todosLosEmpleados = employeeRepository.findAll();

        // 2. Sumamos el tamaño de la lista de horas disponibles de cada uno
        return todosLosEmpleados.stream()
                .mapToInt(emp -> getAvailableHours(emp.getId(), fecha, null).size())
                .sum();
    }

    private List<LocalTime> generateSlotsForInterval(LocalTime start, LocalTime end) {
        List<LocalTime> slots = new ArrayList<>();
        LocalTime current = start;

        while (current.isBefore(end)) {
            slots.add(current);
            current = current.plusMinutes(30);
        }
        return slots;
    }

    private BusinessShift saveShift(BusinessShift businessShift){
        return businessShiftRepository.save(businessShift);
    }

    private void deleteShiftByDay(DayOfWeek day) {
        businessShiftRepository.deleteByDayOfWeek(day);
    }


}
