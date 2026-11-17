package juego;

/**
 * Salas del búnker. Cada sala está en un piso (1 = arriba, 3 = abajo).
 * Las salas son el lugar donde los sobrevivientes se cruzan; los encuentros generan los eventos
 * que modifican el grafo de confianza.
 */
public enum Sala {
    /** Piso 1: entrada y muro de defensa. Puesto de la líder y de los guardias. */
    ENTRADA("Entrada / Muro", 1),
    /** Piso 1: enfermería. Un médico presente calma a los demás. */
    ENFERMERIA("Enfermería", 1),
    /** Piso 2: invernadero. Estar entre plantas baja un poco la paranoia. */
    INVERNADERO("Invernadero", 2),
    /** Piso 2: comedor. El lugar donde más fácil nace la confianza. */
    COMEDOR("Comedor", 2),
    /** Piso 3: filtro de agua. Aquí ocurren las traiciones. */
    FILTRO_AGUA("Filtro de agua", 3),
    /** Piso 3: dormitorios. Descansar baja la paranoia. */
    DORMITORIOS("Dormitorios", 3);

    private final String etiqueta;
    private final int piso;

    Sala(String etiqueta, int piso) {
        this.etiqueta = etiqueta;
        this.piso = piso;
    }

    /** @return nombre legible */
    public String getEtiqueta() {
        return etiqueta;
    }

    /** @return piso del búnker (1 a 3) */
    public int getPiso() {
        return piso;
    }
}
