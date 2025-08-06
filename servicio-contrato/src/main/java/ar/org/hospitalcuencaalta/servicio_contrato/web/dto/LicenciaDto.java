package ar.org.hospitalcuencaalta.servicio_contrato.web.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LicenciaDto {
    private Long id;
    private String tipo;
    private LocalDate fechaDesde;
    private LocalDate fechaHasta;
    private Long empleadoId;
}
