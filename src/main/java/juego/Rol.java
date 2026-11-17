package juego;

/**
 * Rol de un sobreviviente dentro del refugio. Cada rol tiene una sala de trabajo, a la que
 * el sobreviviente tiende a ir en cada turno.
 */
public enum Rol {
    /** Dirige el refugio desde la entrada. Solo hay una líder. */
    LIDER("Líder", Sala.ENTRADA),
    /** Calma la paranoia de quienes están con él en la enfermería. */
    MEDICO("Médico", Sala.ENFERMERIA),
    /** Mantiene el filtro de agua. */
    INGENIERO("Ingeniero", Sala.FILTRO_AGUA),
    /** Vigila la compuerta de entrada. */
    GUARDIA("Guardia", Sala.ENTRADA),
    /** Administra la comida en el comedor. */
    COCINERO("Cocinero", Sala.COMEDOR);

    private final String etiqueta;
    private final Sala salaTrabajo;

    Rol(String etiqueta, Sala salaTrabajo) {
        this.etiqueta = etiqueta;
        this.salaTrabajo = salaTrabajo;
    }

    /** @return nombre legible */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** @return sala donde trabaja este rol */
    public Sala getSalaTrabajo() {
        return salaTrabajo;
    }
}
