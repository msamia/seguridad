// ApiErrorMapper.java
package ar.org.hospitalcuencaalta.servicio_seguridad.web.mapeo;

import ar.org.hospitalcuencaalta.servicio_seguridad.excepcion.ApiError;
import ar.org.hospitalcuencaalta.servicio_seguridad.web.dto.ApiErrorDto;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ApiErrorMapper {
    ApiErrorDto toDto(ApiError error);
}
