package ar.org.hospitalcuencaalta.servicio_seguridad.repositorio;

import ar.org.hospitalcuencaalta.servicio_seguridad.modelo.Empleado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long> {
    Optional<Empleado> findByDocumento(String documento);
}
