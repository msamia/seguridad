package ar.org.hospitalcuencaalta.servicio_seguridad.web.mapeo;

import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Puesto;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.PuestoDetalleDto;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.PuestoDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE,
        uses = EmpleadoMapper.class)
public interface PuestoMapper {
    PuestoDto toDto(Puesto e);

    PuestoDetalleDto toDetalleDto(Puesto e);

    Puesto toEntity(PuestoDto d);
}
