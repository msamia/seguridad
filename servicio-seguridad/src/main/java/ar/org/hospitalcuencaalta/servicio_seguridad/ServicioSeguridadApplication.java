package ar.org.hospitalcuencaalta.servicio_seguridad;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "ar.org.hospitalcuencaalta")
public class ServicioSeguridadApplication {

    public static void main(String[] args) {
        SpringApplication.run(ServicioSeguridadApplication.class, args);
    }

}
