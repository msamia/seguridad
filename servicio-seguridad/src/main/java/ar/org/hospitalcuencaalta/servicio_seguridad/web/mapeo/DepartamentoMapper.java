package ar.org.hospitalcuencaalta.servicio_seguridad.web.mapeo;

import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Departamento;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.DepartamentoDetalleDto;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.DepartamentoDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring",
        unmappedTargetPolicy = org.mapstruct.ReportingPolicy.IGNORE,
        uses = EmpleadoMapper.class)
public interface DepartamentoMapper {
    DepartamentoDto toDto(Departamento e);

    DepartamentoDetalleDto toDetalleDto(Departamento e);

    Departamento toEntity(DepartamentoDto d);
}
