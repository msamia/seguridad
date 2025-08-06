package ar.org.hospitalcuencaalta.servicio_seguridad.web.mapeo;

import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Sindicato;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.SindicatoDetalleDto;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.SindicatoDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE,
        uses = EmpleadoMapper.class)
public interface SindicatoMapper {
    SindicatoDto toDto(Sindicato e);

    SindicatoDetalleDto toDetalleDto(Sindicato e);

    Sindicato toEntity(SindicatoDto d);
}
