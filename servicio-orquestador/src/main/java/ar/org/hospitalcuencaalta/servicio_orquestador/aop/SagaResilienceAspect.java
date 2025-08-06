package ar.org.hospitalcuencaalta.servicio_orquestador.aop;

import ar.org.hospitalcuencaalta.servicio_orquestador.anotacion.SagaStep;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.decorators.Decorators;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PreDestroy;

import java.time.Duration;
import java.util.concurrent.*;

/**
 * Aspecto que aplica CircuitBreaker, RateLimiter, TimeLimiter y Bulkhead
 * en todos los métodos anotados con @SagaStep.
 *
 * Para extraer el nombre del breaker se lee la propiedad breaker() de la anotación.
 */
@Aspect
@Component
public class SagaResilienceAspect {

    private static final Logger log = LoggerFactory.getLogger(SagaResilienceAspect.class);

    private final CircuitBreakerRegistry cbRegistry;
    private final RateLimiterRegistry rlRegistry;
    private final TimeLimiterRegistry tlRegistry;
    private final BulkheadRegistry bhRegistry;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public SagaResilienceAspect(CircuitBreakerRegistry cbRegistry,
                                RateLimiterRegistry rlRegistry,
                                TimeLimiterRegistry tlRegistry,
                                BulkheadRegistry bhRegistry) {
        this.cbRegistry = cbRegistry;
        this.rlRegistry = rlRegistry;
        this.tlRegistry = tlRegistry;
        this.bhRegistry = bhRegistry;
    }

    @PreDestroy
    public void shutdownExecutor() {
        executorService.shutdownNow();
    }

    @Around("@annotation(sagaStepAnno)")
    public Object aroundSagaStepMethods(ProceedingJoinPoint pjp, SagaStep sagaStepAnno) throws Throwable {
        // 1) Extraer nombre del breaker desde la propiedad de la anotación
        String name       = sagaStepAnno.breaker();
        CircuitBreaker cb = cbRegistry.circuitBreaker(name);
        RateLimiter rl    = rlRegistry.rateLimiter(name);
        TimeLimiter tl    = tlRegistry.timeLimiter(name);
        Bulkhead bh       = bhRegistry.bulkhead(name);

        // 2) Crear un Callable que invoque el método original
        Callable<Object> originalCall = () -> {
            try {
                return pjp.proceed();
            } catch (Throwable t) {
                throw new ExecutionException(t);
            }
        };

        // 3) Decorar con CircuitBreaker, RateLimiter y Bulkhead
        Callable<Object> decoratedSync = Decorators
                .ofCallable(originalCall)
                .withCircuitBreaker(cb)
                .withRateLimiter(rl)
                .withBulkhead(bh)
                .decorate();

        // 4) Ejecutar el Callable con TimeLimiter utilizando un executor compartido
        Future<Object> future = executorService.submit(decoratedSync);

        try {
            Duration timeout = tl.getTimeLimiterConfig().getTimeoutDuration();
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException tex) {
            future.cancel(true);
            log.error("[SagaResilienceAspect] Timeout en método {}: {}",
                    ((MethodSignature) pjp.getSignature()).getMethod().getName(), tex.toString());
            throw new TimeoutException("TimeLimiter excedido en método "
                    + ((MethodSignature) pjp.getSignature()).getMethod().getName());
        } catch (ExecutionException ex) {
            log.error("[SagaResilienceAspect] Error en método {}: {}",
                    ((MethodSignature) pjp.getSignature()).getMethod().getName(), ex.getCause().toString());
            throw ex.getCause();
        } catch (Exception ex) {
            log.error("[SagaResilienceAspect] Falla inesperada en método {}: {}",
                    ((MethodSignature) pjp.getSignature()).getMethod().getName(), ex.toString());
            throw ex;
        }
    }
}
