package ar.org.hospitalcuencaalta.servicio_seguridad.web.mapeo;

import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Documentacion;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.DocumentacionDetalleDto;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.DocumentacionDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE,
        uses = EmpleadoMapper.class)
public interface DocumentacionMapper {
    DocumentacionDto toDto(Documentacion e);

    DocumentacionDetalleDto toDetalleDto(Documentacion e);

    Documentacion toEntity(DocumentacionDto d);
}
