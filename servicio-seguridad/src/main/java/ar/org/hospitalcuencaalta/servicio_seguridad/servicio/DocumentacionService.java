package ar.org.hospitalcuencaalta.servicio_seguridad.servicio;

import ar.org.hospitalcuencaalta.servicio_seguridad.excepcion.ResourceNotFoundException;
import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Documentacion;
import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Empleado;
import ar.org.hospitalcuencaalta.servicio_seguridad.repositorio.DocumentacionRepository;
import ar.org.hospitalcuencaalta.servicio_seguridad.repositorio.EmpleadoRepository;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.DocumentacionDetalleDto;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.DocumentacionDto;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.mapeo.DocumentacionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DocumentacionService {

    private final DocumentacionRepository repo;
    private final EmpleadoRepository empleadoRepo;
    private final DocumentacionMapper mapper;

    /**
     * Alta de documentación de un empleado. Se marca transaccional ya que en el
     * futuro podría registrarse actividad adicional o publicar eventos. De esta
     * manera se mantiene la atomicidad de la operación de escritura.
     */
    @Transactional
    public DocumentacionDto create(Long empleadoId, DocumentacionDto dto) {
        Empleado empleado = empleadoRepo.findById(empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", empleadoId));

        Documentacion entidad = mapper.toEntity(dto);
        entidad.setEmpleado(empleado);

        Documentacion saved = repo.save(entidad);
        return mapper.toDto(saved);
    }

    /**
     * Actualización de documentación. La transacción asegura que la obtención
     * del empleado y el guardado de los cambios ocurran de forma conjunta.
     */
    @Transactional
    public DocumentacionDto update(Long empleadoId, Long id, DocumentacionDto dto) {
        Empleado empleado = empleadoRepo.findById(empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Empleado", empleadoId));

        Documentacion entidad = mapper.toEntity(dto);
        entidad.setId(id);
        entidad.setEmpleado(empleado);

        Documentacion saved = repo.save(entidad);
        return mapper.toDto(saved);
    }

    /**
     * Eliminación de un registro de documentación. Se declara transaccional por
     * si en el futuro se agregan acciones adicionales (por ejemplo, auditar o
     * publicar eventos) que deban ocurrir junto con el borrado.
     */
    @Transactional
    public void delete(Long empleadoId, Long id) {
        Documentacion entidad = repo.findByIdAndEmpleadoId(id, empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Documentacion", id));
        repo.delete(entidad);
    }

    public DocumentacionDetalleDto get(Long empleadoId, Long id) {
        Documentacion entidad = repo.findByIdAndEmpleadoId(id, empleadoId)
                .orElseThrow(() -> new ResourceNotFoundException("Documentacion", id));
        return mapper.toDetalleDto(entidad);
    }

    public Page<DocumentacionDetalleDto> allDetailed(Long empleadoId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repo.findByEmpleadoId(empleadoId, pageable)
                .map(mapper::toDetalleDto);
    }

    public Page<DocumentacionDto> all(Long empleadoId, Integer page, Integer size) {
        Pageable pageable = PageRequest.of(page, size);
        return repo.findByEmpleadoId(empleadoId, pageable)
                .map(mapper::toDto);
    }
}

