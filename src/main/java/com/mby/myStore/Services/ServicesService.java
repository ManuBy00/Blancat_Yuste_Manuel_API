package com.mby.myStore.Services;

import com.mby.myStore.Model.Service;
import com.mby.myStore.Repositories.ServiceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.ArrayList;
import java.util.List;

@org.springframework.stereotype.Service
@Transactional
public class ServicesService {
    @Autowired
    ServiceRepository serviceRepository;

    public List<Service> getServicios(){
        if (serviceRepository.findAll().isEmpty()) {
            return new ArrayList<>();
        }
        return serviceRepository.findAll();
    }

    public Service createService(Service service) {
        if (service.getPrice() < 0) {
            throw new IllegalArgumentException("El precio no puede ser negativo");
        }
        return serviceRepository.save(service);
    }

    public void deleteService(Long id) {
        if (!serviceRepository.existsById(id)) {
            throw new EntityNotFoundException("El servicio con ID " + id + " no existe.");
        }
        serviceRepository.deleteById(id);
    }

    @Transactional
    public Service updateService(Long id, Service details) {
        // 1. Buscamos el servicio o lanzamos excepción si no existe
        Service existingService = serviceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("El servicio con ID " + id + " no existe."));

        // 2. Reglas de negocio (validaciones)
        if (details.getPrice() < 0) {
            throw new IllegalArgumentException("El precio del servicio no puede ser negativo.");
        }

        if (details.getDurationMinutes() != null && details.getDurationMinutes() <= 0) {
            throw new IllegalArgumentException("La duración debe ser mayor a 0 minutos.");
        }

        // 3. Actualizamos los campos
        existingService.setName(details.getName());
        existingService.setPrice(details.getPrice());
        existingService.setDurationMinutes(details.getDurationMinutes());

        // 4. Guardamos los cambios
        return serviceRepository.save(existingService);
    }

}
