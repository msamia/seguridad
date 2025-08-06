package ar.org.hospitalcuencaalta.servicio_seguridad.web.mapeo;

import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Empleado;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.EmpleadoDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EmpleadoMapper {
    EmpleadoDto toDto(Empleado e);

    Empleado toEntity(EmpleadoDto d);
}

