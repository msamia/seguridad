package ar.org.hospitalcuencaalta.servicio_seguridad.excepcion;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String mensaje) {
        super(mensaje);
    }
}