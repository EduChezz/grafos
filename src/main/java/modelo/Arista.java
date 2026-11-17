package modelo;

import java.util.Objects;

/**
 * Par de arcos opuestos entre dos estudiantes: {@code A → B} y {@code B → A}.
 * <p>
 * No copia los arcos: guarda referencias a los mismos objetos que están en las listas de
 * adyacencia de los vértices. Sirve para recorrer las relaciones "por pares" (por ejemplo,
 * para dibujarlas o exportarlas) sin repetir cada relación dos veces.
 */
public class Arista {
    private final Arco arcoAB;
    private final Arco arcoBA;

    /**
     * @param arcoAB arco {@code A → B}
     * @param arcoBA arco {@code B → A}
     * @throws IllegalArgumentException si los arcos no son opuestos
     */
    public Arista(Arco arcoAB, Arco arcoBA) {
        Objects.requireNonNull(arcoAB, "arcoAB no puede ser null");
        Objects.requireNonNull(arcoBA, "arcoBA no puede ser null");
        if (arcoAB.getOrigen() != arcoBA.getDestino() || arcoAB.getDestino() != arcoBA.getOrigen()) {
            throw new IllegalArgumentException("Los arcos de una arista deben ser opuestos");
        }
        this.arcoAB = arcoAB;
        this.arcoBA = arcoBA;
    }

    /** @return arco {@code A → B} */
    public Arco getArcoAB() {
        return arcoAB;
    }

    /** @return arco {@code B → A} */
    public Arco getArcoBA() {
        return arcoBA;
    }

    /** @return ID del estudiante A (quien creó la relación) */
    public int getUsuarioA() {
        return arcoAB.getOrigen();
    }

    /** @return ID del estudiante B */
    public int getUsuarioB() {
        return arcoAB.getDestino();
    }

    /**
     * @param idUsuario uno de los dos extremos
     * @return el arco que sale de ese extremo, o {@code null} si no pertenece a la arista
     */
    public Arco getArcoDesde(int idUsuario) {
        if (idUsuario == arcoAB.getOrigen()) return arcoAB;
        if (idUsuario == arcoBA.getOrigen()) return arcoBA;
        return null;
    }

    /**
     * @param idUsuario uno de los dos extremos
     * @return el otro extremo, o -1 si no pertenece a la arista
     */
    public int getOtroUsuario(int idUsuario) {
        if (idUsuario == getUsuarioA()) return getUsuarioB();
        if (idUsuario == getUsuarioB()) return getUsuarioA();
        return -1;
    }

    /**
     * @param idUsuario ID a buscar
     * @return {@code true} si es uno de los dos extremos
     */
    public boolean contieneUsuario(int idUsuario) {
        return idUsuario == getUsuarioA() || idUsuario == getUsuarioB();
    }

    /**
     * @param tipo tipo a comprobar
     * @return {@code true} si los dos sentidos tienen ese tipo
     */
    public boolean esMutua(TipoRelacion tipo) {
        return arcoAB.getTipo() == tipo && arcoBA.getTipo() == tipo;
    }

    @Override
    public String toString() {
        return "[" + arcoAB + " | " + arcoBA + "]";
    }

    /** Dos aristas son iguales si unen a los mismos dos estudiantes, sin importar el orden. */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Arista)) return false;
        Arista otra = (Arista) obj;
        return (getUsuarioA() == otra.getUsuarioA() && getUsuarioB() == otra.getUsuarioB())
            || (getUsuarioA() == otra.getUsuarioB() && getUsuarioB() == otra.getUsuarioA());
    }

    @Override
    public int hashCode() {
        return Objects.hash(Math.min(getUsuarioA(), getUsuarioB()), Math.max(getUsuarioA(), getUsuarioB()));
    }
}
