package ar.org.hospitalcuencaalta.servicio_seguridad.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SindicatoDto {
    private Long id;
    private String nombre;
    private String convenioColectivo;
}
