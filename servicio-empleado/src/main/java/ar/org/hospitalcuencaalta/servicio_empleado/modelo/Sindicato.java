package ar.org.hospitalcuencaalta.servicio_empleado.modelo;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "sindicatos")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Sindicato {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String nombre;
    @Column(name = "convenio_colectivo")
    private String convenioColectivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "empleado_id")
    private Empleado empleado;
}
