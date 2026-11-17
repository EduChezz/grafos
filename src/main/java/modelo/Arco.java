package modelo;

import java.util.Objects;

/**
 * Arco dirigido {@code origen → destino}: lo que un estudiante opina de otro.
 * <p>
 * <b>Cada arco existe una sola vez en memoria.</b> El vértice de origen lo guarda en su lista de
 * salientes, el vértice de destino en su lista de entrantes y la {@link Arista} del par también
 * lo referencia. Son tres referencias al mismo objeto, no tres copias: si se cambia el tipo o el
 * peso, todos ven el cambio.
 * <p>
 * El <b>peso</b> es la afinidad de la relación, en el rango (0, {@value #PESO_MAXIMO}]:
 * a mayor peso, relación más cercana. Lo usan el camino de mayor afinidad (Dijkstra) y la
 * afinidad promedio de las comunidades.
 */
public class Arco {
    /** Peso (afinidad) máximo permitido. */
    public static final double PESO_MAXIMO = 10.0;

    private final int origen;
    private final int destino;
    private TipoRelacion tipo;
    private double peso;

    /**
     * Crea un arco con el peso por defecto de su tipo.
     *
     * @param origen  ID del estudiante que opina
     * @param destino ID del estudiante sobre el que opina
     * @param tipo    tipo de relación
     */
    public Arco(int origen, int destino, TipoRelacion tipo) {
        this(origen, destino, tipo, Objects.requireNonNull(tipo, "El tipo no puede ser null").getPesoPorDefecto());
    }

    /**
     * Crea un arco con un peso explícito.
     *
     * @param origen  ID del estudiante que opina
     * @param destino ID del estudiante sobre el que opina
     * @param tipo    tipo de relación
     * @param peso    afinidad, en el rango (0, {@value #PESO_MAXIMO}]
     * @throws IllegalArgumentException si el peso está fuera de rango
     */
    public Arco(int origen, int destino, TipoRelacion tipo, double peso) {
        this.origen = origen;
        this.destino = destino;
        this.tipo = Objects.requireNonNull(tipo, "El tipo no puede ser null");
        this.peso = validarPeso(peso);
    }

    /**
     * Comprueba que un peso sea un número finito en el rango (0, {@value #PESO_MAXIMO}].
     *
     * @param peso valor a validar
     * @return el mismo peso, si es válido
     * @throws IllegalArgumentException si no es válido
     */
    public static double validarPeso(double peso) {
        if (Double.isNaN(peso) || Double.isInfinite(peso) || peso <= 0 || peso > PESO_MAXIMO) {
            throw new IllegalArgumentException("El peso (afinidad) debe ser mayor que 0 y como máximo " + PESO_MAXIMO);
        }
        return peso;
    }

    /** @return ID del estudiante que opina */
    public int getOrigen() {
        return origen;
    }

    /** @return ID del estudiante sobre el que se opina */
    public int getDestino() {
        return destino;
    }

    /** @return tipo de relación de este sentido */
    public TipoRelacion getTipo() {
        return tipo;
    }

    /**
     * @param tipo nuevo tipo de relación
     */
    public void setTipo(TipoRelacion tipo) {
        this.tipo = Objects.requireNonNull(tipo, "El tipo no puede ser null");
    }

    /** @return afinidad de la relación */
    public double getPeso() {
        return peso;
    }

    /**
     * @param peso nueva afinidad, en el rango (0, {@value #PESO_MAXIMO}]
     * @throws IllegalArgumentException si está fuera de rango
     */
    public void setPeso(double peso) {
        this.peso = validarPeso(peso);
    }

    @Override
    public String toString() {
        return "(" + origen + " -> " + destino + ", " + tipo + ", peso=" + peso + ")";
    }

    /** Dos arcos son iguales si unen el mismo origen con el mismo destino. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Arco)) return false;
        Arco otro = (Arco) obj;
        return origen == otro.origen && destino == otro.destino;
    }

    @Override
    public int hashCode() {
        return Objects.hash(origen, destino);
    }
}
