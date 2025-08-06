package ar.org.hospitalcuencaalta.servicio_seguridad.excepcion;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String entidad, Object id) {
        super(String.format("%s con id '%s' no encontrado", entidad, id));
    }
}


