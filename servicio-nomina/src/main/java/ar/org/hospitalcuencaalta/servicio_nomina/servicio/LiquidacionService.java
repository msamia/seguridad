package ar.org.hospitalcuencaalta.servicio_nomina.servicio;

import ar.org.hospitalcuencaalta.servicio_nomina.modelo.Liquidacion;
import ar.org.hospitalcuencaalta.servicio_nomina.modelo.ConceptoLiquidacion;
import ar.org.hospitalcuencaalta.servicio_nomina.modelo.TipoCalculo;
import ar.org.hospitalcuencaalta.servicio_nomina.repositorio.LiquidacionRepository;
import ar.org.hospitalcuencaalta.servicio_nomina.repositorio.ConceptoLiquidacionRepository;
import ar.org.hospitalcuencaalta.servicio_nomina.repositorio.EmpleadoConceptoRepository;
import ar.org.hospitalcuencaalta.servicio_nomina.repositorio.EmpleadoRegistryRepository;
import ar.org.hospitalcuencaalta.servicio_nomina.feign.EmpleadoClient;
import ar.org.hospitalcuencaalta.servicio_nomina.web.dto.EmpleadoRegistryDto;
import feign.FeignException;
import ar.org.hospitalcuencaalta.servicio_nomina.modelo.EmpleadoRegistry;
import org.springframework.web.server.ResponseStatusException;
import ar.org.hospitalcuencaalta.servicio_nomina.web.dto.LiquidacionDetalleDto;
import ar.org.hospitalcuencaalta.servicio_nomina.web.dto.LiquidacionDto;
import ar.org.hospitalcuencaalta.servicio_nomina.web.mapeo.LiquidacionDetalleMapper;
import ar.org.hospitalcuencaalta.servicio_nomina.web.mapeo.LiquidacionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LiquidacionService {
    @Autowired
    private LiquidacionRepository repo;
    @Autowired
    private ConceptoLiquidacionRepository conceptoRepo;
    @Autowired
    private EmpleadoConceptoRepository empleadoConceptoRepo;
    @Autowired
    private EmpleadoRegistryRepository empleadoRegistryRepo;
    @Autowired
    private EmpleadoClient empleadoClient;
    @Autowired
    private LiquidacionMapper mapper;
    @Autowired
    private LiquidacionDetalleMapper detalleMapper;
    @Autowired
    private KafkaTemplate<String, Object> kafka;

    /**
     * Creación de la liquidación de haberes. Se ejecuta en una transacción para
     * que todas las validaciones de empleado y el guardado queden agrupados. El
     * evento en Kafka se enviará una vez confirmados los cambios.
     */
    @Transactional
    public LiquidacionDto create(LiquidacionDto dto) {
        if (dto.getEmpleadoId() == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "empleadoId es obligatorio");
        }

        // Check if there is already a liquidation for the given period and employee
        if (repo.findByPeriodoAndEmpleadoId(dto.getPeriodo(), dto.getEmpleadoId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ya existe una liquidacion para el periodo " + dto.getPeriodo());
        }

        EmpleadoRegistryDto emp;
        try {
            emp = empleadoClient.getById(dto.getEmpleadoId());
        } catch (FeignException.NotFound nf) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Empleado con id=" + dto.getEmpleadoId() + " no existe");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Error al validar empleado: " + ex.getMessage(), ex);
        }

        if (!empleadoRegistryRepo.existsByIdAndDocumento(emp.getId(), emp.getDocumento())) {
            if (empleadoRegistryRepo.existsByDocumento(emp.getDocumento())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Ya existe un empleado con documento " + emp.getDocumento());
            }
            empleadoRegistryRepo.save(EmpleadoRegistry.builder()
                    .id(emp.getId())
                    .documento(emp.getDocumento())
                    .nombre(emp.getNombre())
                    .apellido(emp.getApellido())
                    .build());
        }

        Liquidacion e = mapper.toEntity(dto);
        Liquidacion saved = repo.save(e);
        LiquidacionDto out = mapper.toDto(saved);
        kafka.send("servicioNomina.nomina.generated", out);
        return out;
    }

    public List<LiquidacionDto> findAll() {
        return repo.findAll().stream().map(mapper::toDto).collect(Collectors.toList());
    }

    public LiquidacionDetalleDto getDetalle(Long id) {
        return detalleMapper.toDetalleDto(repo.findById(id).orElseThrow());
    }

    /**
     * Cálculo del detalle de la nómina. Se declara @Transactional para
     * garantizar que el cálculo y la actualización de la entidad sean
     * consistentes. Si se amplía este método con pasos adicionales, todos
     * quedarán bajo la misma transacción.
     */
    @Transactional
    public LiquidacionDetalleDto calcularNomina(Long liquidacionId) {
        Liquidacion liq = repo.findById(liquidacionId).orElseThrow();
        java.math.BigDecimal base = liq.getSueldoBruto() == null
                ? java.math.BigDecimal.ZERO
                : liq.getSueldoBruto();

        java.math.BigDecimal adicionales = java.math.BigDecimal.ZERO;
        java.math.BigDecimal descuentos = java.math.BigDecimal.ZERO;

        for (var ec : empleadoConceptoRepo.findByEmpleadoId(liq.getEmpleadoId())) {
            ConceptoLiquidacion c = ec.getConcepto();
            java.math.BigDecimal monto = c.getMonto() == null
                    ? java.math.BigDecimal.ZERO
                    : c.getMonto();
            if (c.getTipoCalculo() == TipoCalculo.PORCENTAJE) {
                monto = base.multiply(monto).divide(java.math.BigDecimal.valueOf(100));
            }
            if (c.getTipoCalculo() == TipoCalculo.RESTA) {
                descuentos = descuentos.add(monto);
            } else {
                adicionales = adicionales.add(monto);
            }
        }

        java.math.BigDecimal sueldoBruto = base.add(adicionales);
        java.math.BigDecimal sueldoNeto = sueldoBruto.subtract(descuentos);

        liq.setSueldoBruto(sueldoBruto);
        liq.setDescuentos(descuentos);
        liq.setSueldoNeto(sueldoNeto);
        repo.save(liq);

        return detalleMapper.toDetalleDto(liq);
    }
}
