package modelo;

/**
 * Tipo de un arco: qué opina el estudiante de origen sobre el de destino.
 * <p>
 * La amistad se modela con <b>arcos</b> (grafo dirigido) porque puede no ser recíproca:
 * a Pedro le puede caer bien María sin que a María le caiga bien Pedro. Cuando ambos arcos
 * de un par son {@link #AMISTAD}, la amistad es <b>mutua</b>.
 */
public enum TipoRelacion {
    /** Todavía no hay opinión. Es el tipo del arco de vuelta al crear una relación. */
    DESCONOCIDO("Desconocido", 1.0),
    /** El origen considera amigo al destino. Es el único tipo que usan los análisis. */
    AMISTAD("Amistad", 1.0),
    /** El origen tiene una mala relación con el destino. */
    ENEMISTAD("Enemistad", 1.0);

    private final String etiqueta;
    private final double pesoPorDefecto;

    TipoRelacion(String etiqueta, double pesoPorDefecto) {
        this.etiqueta = etiqueta;
        this.pesoPorDefecto = pesoPorDefecto;
    }

    /** @return nombre legible para mostrar */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** @return peso (afinidad) que recibe un arco nuevo de este tipo */
    public double getPesoPorDefecto() {
        return pesoPorDefecto;
    }
}
