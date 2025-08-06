package ar.org.hospitalcuencaalta.servicio_orquestador.controlador;

import ar.org.hospitalcuencaalta.servicio_orquestador.modelo.Estados;
import ar.org.hospitalcuencaalta.servicio_orquestador.modelo.Eventos;
import ar.org.hospitalcuencaalta.servicio_orquestador.servicio.SagaStateService;
import ar.org.hospitalcuencaalta.servicio_orquestador.web.dto.ContratoLaboralDto;
import ar.org.hospitalcuencaalta.servicio_orquestador.web.dto.EmpleadoDto;
import ar.org.hospitalcuencaalta.servicio_orquestador.web.dto.SagaEmpleadoContratoRequest;
import ar.org.hospitalcuencaalta.servicio_orquestador.web.dto.SagaStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.statemachine.StateMachine;
import org.springframework.statemachine.config.StateMachineFactory;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import reactor.core.publisher.Flux;

import java.time.Instant;
import java.util.Map;

/**
 * Controlador REST que expone el endpoint para iniciar la SAGA
 * de creación de empleado + contrato.
 */
@RestController
@RequestMapping("/api/saga")
@Tag(name = "SAGA Empleado-Contrato", description = "Orquestación de empleados y contratos")
public class SagaController {

    private final StateMachineFactory<Estados, Eventos> stateMachineFactory;
    private final SagaStateService sagaStateService;
    private final ObjectMapper objectMapper;

    private static final long WAIT_TIMEOUT_MS = 10_000L;

    @Autowired
    public SagaController(StateMachineFactory<Estados, Eventos> stateMachineFactory,
                          SagaStateService sagaStateService,
                          ObjectMapper objectMapper) {
        this.stateMachineFactory = stateMachineFactory;
        this.sagaStateService = sagaStateService;
        this.objectMapper = objectMapper;
    }

    /**
     * Espera de forma bloqueante a que la máquina alcance un estado final.
     * Devuelve {@code true} si completó dentro del timeout configurado.
     */
    private boolean waitForCompletion(StateMachine<Estados, Eventos> stateMachine) {
        long start = System.currentTimeMillis();
        while (!stateMachine.isComplete()
                && System.currentTimeMillis() - start < WAIT_TIMEOUT_MS) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return stateMachine.isComplete();
    }

    @PostMapping("/empleado-contrato")
    @Operation(summary = "Iniciar SAGA", description = "Comienza la orquestación de creación de empleado y contrato")
    public ResponseEntity<SagaStatusResponse> iniciarSaga(@RequestBody SagaEmpleadoContratoRequest request) {
        // 1) Crear un nuevo StateMachine de SAGA por cada petición
        StateMachine<Estados, Eventos> stateMachine =

                stateMachineFactory.getStateMachine();
        // 1.1) Guardar DTOs en extendedState antes de iniciar la máquina
        stateMachine.getExtendedState().getVariables()
                .put("empleadoDto", request.getEmpleado());
        stateMachine.getExtendedState().getVariables()
                .put("contratoDto", request.getContrato());
        stateMachine.getExtendedState().getVariables()
                .put("operacion", "CREAR");

        // Iniciar la máquina de estados de forma sincrónica para garantizar
        // que todas las transiciones posteriores se procesen correctamente.
        Instant inicio = Instant.now();

        stateMachine.start();

        sagaStateService.save(stateMachine);

        // 3) Enviar evento SOLICITAR_CREAR_EMPLEADO de forma bloqueante
        Long sagaId = (Long) stateMachine.getExtendedState().getVariables().get("sagaDbId");
        Message<Eventos> mensaje = MessageBuilder.withPayload(Eventos.SOLICITAR_CREAR_EMPLEADO)
                .setHeader("sagaId", sagaId)
                .build();
        stateMachine.sendEvents(Flux.just(mensaje)).blockLast();

        // Esperar a que la máquina alcance un estado final con timeout
        boolean completed = waitForCompletion(stateMachine);
        if (!completed) {
            stateMachine.getExtendedState().getVariables().putIfAbsent(
                    "mensajeError",
                    "Tiempo de espera agotado al contactar servicio-empleado");
        }

        sagaStateService.save(stateMachine);

        Long empId = stateMachine.getExtendedState().get("idEmpleado", Long.class);
        Long conId = stateMachine.getExtendedState().get("idContrato", Long.class);
        String mensajeError = stateMachine.getExtendedState().get("mensajeError", String.class);

        // 4) Devolver estado final de la SAGA
        SagaStatusResponse resp = SagaStatusResponse.builder()
                .sagaId(String.valueOf(sagaId))
                .estadoActual(stateMachine.getState().getId().name())
                .idEmpleadoCreado(empId)
                .idContratoCreado(conId)
                .mensajeError(mensajeError)
                .timestampInicio(inicio)
                .timestampFin(Instant.now())
                .build();

        if (stateMachine.getState().getId() == Estados.FINALIZADA) {
            return ResponseEntity.ok(resp);
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(resp);
    }

    @PutMapping("/empleado-contrato/{id}")
    @Operation(summary = "Actualizar SAGA", description = "Modifica empleado y contrato de forma coordinada")
    public SagaStatusResponse actualizarSaga(@PathVariable("id") Long id,
                                             @RequestBody SagaEmpleadoContratoRequest request) {
        StateMachine<Estados, Eventos> stateMachine = stateMachineFactory.getStateMachine();

        Map<Object, Object> vars = stateMachine.getExtendedState().getVariables();

        if (id != null) {
            vars.put("idEmpleado", id);
        } else {
            vars.remove("idEmpleado");
        }

        EmpleadoDto empleado = request.getEmpleado();
        if (empleado != null) {
            vars.put("empleadoDto", empleado);
        } else {
            vars.remove("empleadoDto");
        }

        ContratoLaboralDto contrato = request.getContrato();
        if (contrato != null) {
            vars.put("contratoDto", contrato);
            Long idContrato = contrato.getId();
            if (idContrato != null) {
                vars.put("idContrato", idContrato);
            } else {
                vars.remove("idContrato");
            }
        } else {
            vars.remove("contratoDto");
            vars.remove("idContrato");
        }
        vars.put("operacion", "ACTUALIZAR");

        // Ejecutar la actualización de forma sincrónica para poder devolver los
        // IDs generados o actualizados en la respuesta.
        Instant inicio = Instant.now();

        stateMachine.start();
        sagaStateService.save(stateMachine);

        Long sagaId = (Long) stateMachine.getExtendedState().getVariables().get("sagaDbId");
        Message<Eventos> msg = MessageBuilder.withPayload(Eventos.SOLICITAR_ACTUALIZAR_EMPLEADO)
                .setHeader("sagaId", sagaId)
                .build();

        // Usamos blockLast() para esperar a que el evento se procese
        stateMachine.sendEvents(Flux.just(msg)).blockLast();

        boolean completed = waitForCompletion(stateMachine);
        if (!completed) {
            stateMachine.getExtendedState().getVariables().putIfAbsent(
                    "mensajeError",
                    "Tiempo de espera agotado al contactar servicio-empleado");
        }

        sagaStateService.save(stateMachine);

        Long empId = stateMachine.getExtendedState().get("idEmpleado", Long.class);
        Long conId = stateMachine.getExtendedState().get("idContrato", Long.class);
        String mensajeError = stateMachine.getExtendedState().get("mensajeError", String.class);

        return SagaStatusResponse.builder()
                .sagaId(String.valueOf(sagaId))
                .estadoActual(stateMachine.getState().getId().name())
                .idEmpleadoCreado(empId)
                .idContratoCreado(conId)
                .mensajeError(mensajeError)
                .timestampInicio(inicio)
                .timestampFin(Instant.now())
                .build();
    }

    @DeleteMapping("/empleado-contrato/{id}")
    @Operation(summary = "Eliminar SAGA", description = "Elimina empleado y contrato mediante orquestación")
    public SagaStatusResponse eliminarSaga(@PathVariable("id") Long id,
                                            @RequestParam("contratoId") Long contratoId) {
        StateMachine<Estados, Eventos> stateMachine = stateMachineFactory.getStateMachine();

        stateMachine.getExtendedState().getVariables().put("idEmpleado", id);
        stateMachine.getExtendedState().getVariables().put("idContrato", contratoId);
        stateMachine.getExtendedState().getVariables().put("operacion", "ELIMINAR");

        Instant inicio = Instant.now();

        stateMachine.start();
        sagaStateService.save(stateMachine);

        Long sagaId = (Long) stateMachine.getExtendedState().getVariables().get("sagaDbId");
        Message<Eventos> msg = MessageBuilder.withPayload(Eventos.SOLICITAR_ELIMINAR_CONTRATO)
                .setHeader("sagaId", sagaId)
                .build();
        stateMachine.sendEvents(Flux.just(msg)).blockLast();

        boolean completed = waitForCompletion(stateMachine);
        if (!completed) {
            stateMachine.getExtendedState().getVariables().putIfAbsent(
                    "mensajeError",
                    "Tiempo de espera agotado al contactar servicio-empleado");
        }

        sagaStateService.save(stateMachine);

        Long empId = stateMachine.getExtendedState().get("idEmpleado", Long.class);
        Long conId = stateMachine.getExtendedState().get("idContrato", Long.class);
        String mensajeError = stateMachine.getExtendedState().get("mensajeError", String.class);

        return SagaStatusResponse.builder()
                .sagaId(String.valueOf(sagaId))
                .estadoActual(stateMachine.getState().getId().name())
                .idEmpleadoCreado(empId)
                .idContratoCreado(conId)
                .mensajeError(mensajeError)
                .timestampInicio(inicio)
                .timestampFin(Instant.now())
                .build();
    }

    @GetMapping("/empleado-contrato/{id}")
    @Operation(summary = "Estado de SAGA", description = "Obtiene el estado actual de la SAGA")
    public ResponseEntity<SagaStatusResponse> obtenerEstado(@PathVariable("id") Long id) {


        return sagaStateService.findById(id)
                .map(state -> {
                    Map<String, Object> ext;
                    try {
                        ext = objectMapper.readValue(state.getExtendedState(), Map.class);
                    } catch (Exception e) {
                        ext = Map.of();
                    }

                    Long empId = ext.get("idEmpleado") instanceof Number
                            ? ((Number) ext.get("idEmpleado")).longValue() : null;
                    Long conId = ext.get("idContrato") instanceof Number
                            ? ((Number) ext.get("idContrato")).longValue() : null;
                    String msg = ext.get("mensajeError") instanceof String
                            ? (String) ext.get("mensajeError") : null;

                    return SagaStatusResponse.builder()
                            .sagaId(state.getSagaId().toString())
                            .estadoActual(state.getEstado().name())
                            .idEmpleadoCreado(empId)
                            .idContratoCreado(conId)
                            .mensajeError(msg)
                            .timestampInicio(null)
                            .timestampFin(state.getUpdatedAt())
                            .build();
                })
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
